from datetime import timedelta
from decimal import Decimal
from django.contrib.auth.models import User
from django.db import IntegrityError, transaction
from django.test import TestCase, Client
from django.utils import timezone
from .models import Business, Slot, Booking, WaitEntry, Notice
from .services import book, join_waitlist, cancel_booking, leave_waitlist, cancel_slot, refresh_offers, RuleError
from .forms import SlotForm, save_slot

class BookingTests(TestCase):
    def setUp(self):
        self.owner=User.objects.create_user('owner',password='Example-only-password!42')
        self.a=User.objects.create_user('alice',password='Example-only-password!42')
        self.b=User.objects.create_user('bob',password='Example-only-password!42')
        self.c=User.objects.create_user('carol',password='Example-only-password!42')
        self.biz=Business.objects.create(owner=self.owner,name='Test Studio',category='Wellness',city='Toronto',address='Test address',description='Test business')
        self.slot=Slot.objects.create(business=self.biz,service='Test session',starts=timezone.now()+timedelta(days=2),duration=60,price=Decimal('50.00'))
    def test_book_and_duplicate_blocked(self):
        book(self.slot.pk,self.a)
        with self.assertRaises(RuleError): book(self.slot.pk,self.b)
        self.assertEqual(Booking.objects.filter(active=True).count(),1)
    def test_database_enforces_single_booking(self):
        book(self.slot.pk,self.a)
        with self.assertRaises(IntegrityError),transaction.atomic(): Booking.objects.create(slot=self.slot,customer=self.b)
    def test_cancellation_offers_fifo_then_claim(self):
        booking=book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        join_waitlist(self.slot.pk,self.c)
        cancel_booking(booking.pk,self.a)
        self.assertEqual(WaitEntry.objects.get(customer=self.b).status,'offered')
        with self.assertRaises(RuleError): book(self.slot.pk,self.c)
        book(self.slot.pk,self.b)
        self.assertEqual(WaitEntry.objects.get(customer=self.b).status,'accepted')
        self.assertTrue(Booking.objects.filter(customer=self.b,active=True).exists())
    def test_expired_offer_moves_to_next(self):
        booking=book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        join_waitlist(self.slot.pk,self.c)
        cancel_booking(booking.pk,self.a)
        WaitEntry.objects.filter(customer=self.b).update(expires=timezone.now()-timedelta(seconds=1))
        refresh_offers()
        self.assertEqual(WaitEntry.objects.get(customer=self.b).status,'expired')
        self.assertEqual(WaitEntry.objects.get(customer=self.c).status,'offered')
    def test_decline_moves_offer(self):
        booking=book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        join_waitlist(self.slot.pk,self.c)
        cancel_booking(booking.pk,self.a)
        leave_waitlist(WaitEntry.objects.get(customer=self.b).pk,self.b)
        self.assertEqual(WaitEntry.objects.get(customer=self.c).status,'offered')
    def test_duplicate_waitlist_and_owner_rejected(self):
        booking=book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        for person in [self.a,self.b,self.owner]:
            with self.assertRaises(RuleError): join_waitlist(self.slot.pk,person)
    def test_available_slot_does_not_allow_waitlist(self):
        with self.assertRaises(RuleError): join_waitlist(self.slot.pk,self.b)
    def test_past_slot_rejected(self):
        self.slot.starts=timezone.now()-timedelta(minutes=1)
        self.slot.save()
        with self.assertRaises(RuleError): book(self.slot.pk,self.a)
    def test_owner_cannot_book_own_slot(self):
        with self.assertRaises(RuleError): book(self.slot.pk,self.owner)
    def test_customer_cannot_cancel_someone_elses_booking(self):
        booking=book(self.slot.pk,self.a)
        self.client.force_login(self.b)
        self.assertEqual(self.client.post(f'/action/cancel-booking/{booking.pk}/').status_code,404)
        booking.refresh_from_db()
        self.assertTrue(booking.active)
    def test_customer_cannot_cancel_business_slot(self):
        self.client.force_login(self.a)
        self.assertEqual(self.client.post(f'/action/cancel-slot/{self.slot.pk}/').status_code,404)
    def test_business_cancellation_closes_booking_and_waitlist(self):
        book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        cancel_slot(self.slot.pk,self.owner)
        self.assertFalse(Booking.objects.filter(active=True).exists())
        self.assertEqual(WaitEntry.objects.get().status,'expired')
        with self.assertRaises(RuleError): book(self.slot.pk,self.c)
    def test_write_endpoints_require_post_and_login(self):
        self.assertEqual(self.client.post(f'/action/book/{self.slot.pk}/').status_code,302)
        self.client.force_login(self.a)
        self.assertEqual(self.client.get(f'/action/book/{self.slot.pk}/').status_code,405)
    def test_csrf_required(self):
        client=Client(enforce_csrf_checks=True)
        client.force_login(self.a)
        self.assertEqual(client.post(f'/action/book/{self.slot.pk}/').status_code,403)
    def test_all_screens_render(self):
        self.assertContains(self.client.get('/'),'Find your next opening')
        self.assertEqual(self.client.get('/login/').status_code,200)
        self.assertEqual(self.client.get('/signup/').status_code,200)
        self.client.force_login(self.a)
        for url in ['/dashboard/','/business/edit/']: self.assertEqual(self.client.get(url).status_code,200)
        self.client.force_login(self.owner)
        for url in ['/business/','/business/new-slot/']: self.assertEqual(self.client.get(url).status_code,200)
    def test_filters(self):
        self.assertContains(self.client.get('/?city=Toronto&category=Wellness&q=session'),'Test session')
        self.assertNotContains(self.client.get('/?city=Ottawa'),'Test session')
    def test_overlap_rejected_and_adjacent_allowed(self):
        for offset,valid in [(30,False),(60,True)]:
            form=SlotForm({'service':'New session','starts':self.slot.starts+timedelta(minutes=offset),'duration':30,'price':'20'})
            self.assertTrue(form.is_valid(),form.errors)
            if valid: save_slot(form,self.biz)
            else:
                from django.core.exceptions import ValidationError
                with self.assertRaises(ValidationError): save_slot(form,self.biz)
    def test_signup_password_validation(self):
        self.client.post('/signup/',{'username':'weak','password1':'123','password2':'123'})
        self.assertFalse(User.objects.filter(username='weak').exists())
    def test_login_and_throttling(self):
        for i in range(10): self.client.post('/login/',{'username':'alice','password':'wrong'})
        response=self.client.post('/login/',{'username':'alice','password':'Example-only-password!42'})
        self.assertContains(response,'Too many attempts')
    def test_successful_signin(self):
        self.assertRedirects(self.client.post('/login/',{'username':'alice','password':'Example-only-password!42'}),'/dashboard/')
    def test_user_content_escaped(self):
        self.slot.service='<script>alert(1)</script>'
        self.slot.save()
        response=self.client.get('/')
        self.assertNotContains(response,'<script>alert(1)</script>')
        self.assertContains(response,'&lt;script&gt;')
    def test_notifications_private(self):
        Notice.objects.create(user=self.a,text='Private test notice')
        self.client.force_login(self.b)
        self.assertNotContains(self.client.get('/dashboard/'),'Private test notice')
    def test_last_waiter_leaving_reopens_slot(self):
        booking=book(self.slot.pk,self.a)
        join_waitlist(self.slot.pk,self.b)
        cancel_booking(booking.pk,self.a)
        leave_waitlist(WaitEntry.objects.get(customer=self.b).pk,self.b)
        book(self.slot.pk,self.c)
        self.assertTrue(Booking.objects.filter(customer=self.c,active=True).exists())

    def test_full_http_customer_business_waitlist_journey(self):
        self.client.force_login(self.owner)
        start=timezone.localtime()+timedelta(days=4)
        response=self.client.post('/business/new-slot/',{'service':'HTTP journey','starts':start.strftime('%Y-%m-%dT%H:%M'),'duration':45,'price':'35'})
        self.assertRedirects(response,'/business/')
        slot=Slot.objects.get(service='HTTP journey')
        self.client.force_login(self.a)
        self.assertRedirects(self.client.post(f'/action/book/{slot.pk}/'),'/dashboard/')
        self.client.force_login(self.b)
        self.assertRedirects(self.client.post(f'/action/wait/{slot.pk}/'),'/dashboard/')
        self.assertContains(self.client.get('/dashboard/'),'Waiting')
        self.client.force_login(self.a)
        booking=Booking.objects.get(slot=slot,active=True)
        self.client.post(f'/action/cancel-booking/{booking.pk}/')
        self.client.force_login(self.b)
        self.assertContains(self.client.get('/dashboard/'),'Accept &amp; book')
        self.client.post(f'/action/book/{slot.pk}/')
        self.assertContains(self.client.get('/dashboard/'),'Confirmed')
        self.client.force_login(self.owner)
        self.assertContains(self.client.get('/business/'),'bob')

    def test_signup_and_business_creation(self):
        response=self.client.post('/signup/',{'username':'new-person','password1':'Unique-test-password!786','password2':'Unique-test-password!786'})
        self.assertRedirects(response,'/dashboard/')
        response=self.client.post('/business/edit/',{'name':'New studio','category':'Beauty','city':'Toronto','address':'Test only','description':'Test only'})
        self.assertRedirects(response,'/business/')
        self.assertTrue(Business.objects.filter(owner__username='new-person').exists())
