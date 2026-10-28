from datetime import timedelta, datetime
from decimal import Decimal
from unittest.mock import patch
from django.contrib.auth.models import User
from django.test import TestCase, Client
from django.utils import timezone
from .models import Business, Slot, Booking, WaitEntry, Notice
from .services import book, join_waitlist, cancel_booking, reschedule_booking, RuleError

class WorkspaceTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user('calendar-owner')
        self.other_owner = User.objects.create_user('other-owner')
        self.customer = User.objects.create_user('customer')
        self.waiter = User.objects.create_user('waiter')
        self.biz = Business.objects.create(owner=self.owner, name='Calendar Studio', category='Wellness', city='Toronto', address='Test', description='Test')
        self.other_biz = Business.objects.create(owner=self.other_owner, name='Private Other Studio', category='Wellness', city='Toronto', address='Test', description='Test')
        self.start = timezone.localtime().replace(hour=10, minute=0, second=0, microsecond=0) + timedelta(days=14)
        self.old = self.slot(self.biz, self.start)
        self.new = self.slot(self.biz, self.start+timedelta(hours=2), price=65)
        self.booking = book(self.old.pk, self.customer)
        self.client.force_login(self.customer)

    def slot(self, business, start, **kwargs):
        return Slot.objects.create(business=business, starts=start, service=kwargs.get('service','Yoga'), duration=kwargs.get('duration',30), price=kwargs.get('price',50))

    def test_reschedule_moves_and_offers_original_to_waiter(self):
        join_waitlist(self.old.pk, self.waiter)
        replacement = reschedule_booking(self.booking.pk, self.new.pk, self.customer)
        self.booking.refresh_from_db()
        self.assertFalse(self.booking.active)
        self.assertEqual(self.booking.rescheduled_to, replacement)
        self.assertEqual(replacement.slot.price, Decimal('65'))
        self.assertEqual(WaitEntry.objects.get(slot=self.old).status, 'offered')
        self.assertEqual(Booking.objects.filter(customer=self.customer,active=True).count(), 1)

    def test_taken_target_keeps_original_and_notices_unchanged(self):
        book(self.new.pk, self.waiter)
        count = Notice.objects.count()
        with self.assertRaises(RuleError): reschedule_booking(self.booking.pk, self.new.pk, self.customer)
        self.booking.refresh_from_db()
        self.assertTrue(self.booking.active)
        self.assertEqual(Notice.objects.count(), count)

    def test_waitlist_priority_is_respected(self):
        target_booking = book(self.new.pk, self.waiter)
        third = User.objects.create_user('third')
        join_waitlist(self.new.pk, third)
        cancel_booking(target_booking.pk, self.waiter)
        with self.assertRaises(RuleError): reschedule_booking(self.booking.pk, self.new.pk, self.customer)
        self.booking.refresh_from_db()
        self.assertTrue(self.booking.active)

    def test_customer_can_reschedule_into_own_waitlist_offer(self):
        target_booking = book(self.new.pk, self.waiter)
        join_waitlist(self.new.pk, self.customer)
        cancel_booking(target_booking.pk, self.waiter)
        reschedule_booking(self.booking.pk, self.new.pk, self.customer)
        self.assertEqual(WaitEntry.objects.get(slot=self.new).status,'accepted')

    def test_incompatible_service_business_duration_and_same_slot_rejected(self):
        targets = [self.old, self.slot(self.other_biz,self.start), self.slot(self.biz,self.start+timedelta(hours=4),service='Different'), self.slot(self.biz,self.start+timedelta(hours=6),duration=60)]
        for target in targets:
            with self.subTest(target=target.pk), self.assertRaises(RuleError):
                reschedule_booking(self.booking.pk,target.pk,self.customer)
        self.booking.refresh_from_db()
        self.assertTrue(self.booking.active)

    def test_past_cancelled_and_repeated_reschedules_rejected(self):
        reschedule_booking(self.booking.pk,self.new.pk,self.customer)
        with self.assertRaises(RuleError): reschedule_booking(self.booking.pk,self.new.pk,self.customer)
        replacement=Booking.objects.get(slot=self.new,active=True)
        Slot.objects.filter(pk=self.new.pk).update(starts=timezone.now()-timedelta(hours=1))
        with self.assertRaises(RuleError): reschedule_booking(replacement.pk,self.old.pk,self.customer)

    def test_failure_after_replacement_rolls_back_everything(self):
        count=Booking.objects.count()
        with patch('booking.services.rotate', side_effect=[None,RuntimeError('simulated failure')]):
            with self.assertRaises(RuntimeError): reschedule_booking(self.booking.pk,self.new.pk,self.customer)
        self.booking.refresh_from_db()
        self.assertTrue(self.booking.active)
        self.assertEqual(Booking.objects.count(),count)

    def test_http_journey_and_history(self):
        url=f'/bookings/{self.booking.pk}/reschedule/'
        self.assertContains(self.client.get('/dashboard/'),'Reschedule appointment')
        self.assertContains(self.client.get(url),'65.00 CAD')
        self.assertRedirects(self.client.post(url,{'appointment':self.new.pk}),'/dashboard/')
        self.assertContains(self.client.get('/dashboard/'),'Rescheduled')

    def test_owner_and_csrf_protection(self):
        url=f'/bookings/{self.booking.pk}/reschedule/'
        self.client.force_login(self.waiter)
        self.assertEqual(self.client.get(url).status_code,404)
        self.assertEqual(self.client.post(url,{'appointment':self.new.pk}).status_code,404)
        strict=Client(enforce_csrf_checks=True); strict.force_login(self.customer)
        self.assertEqual(strict.post(url,{'appointment':self.new.pk}).status_code,403)
        strict.logout(); self.assertEqual(strict.get(url).status_code,302)

    def test_no_choices_and_invalid_post_keep_booking(self):
        book(self.new.pk,self.waiter)
        url=f'/bookings/{self.booking.pk}/reschedule/'
        self.assertContains(self.client.get(url),'No alternative times yet')
        self.client.post(url,{'appointment':self.new.pk})
        self.booking.refresh_from_db(); self.assertTrue(self.booking.active)

    def test_calendar_owner_scope_and_analytics(self):
        foreign=self.slot(self.other_biz,self.start)
        book(foreign.pk,self.waiter)
        join_waitlist(self.old.pk,self.waiter)
        reschedule_booking(self.booking.pk,self.new.pk,self.customer)
        book(self.old.pk,self.waiter)
        self.client.force_login(self.owner)
        day=timezone.localdate(self.start).isoformat()
        response=self.client.get('/business/calendar/',{'date':day,'view':'day'})
        self.assertEqual(response.status_code,200)
        self.assertNotContains(response,'Private Other Studio')
        stats=response.context['stats']
        self.assertEqual(stats,{'confirmed':2,'cancelled':0,'rescheduled':1,'waitlist':1,'value':Decimal('115')})
        self.assertEqual(sum(len(g['slots']) for g in response.context['groups']),2)
        cancel_booking(Booking.objects.get(slot=self.old,active=True).pk,self.waiter)
        response=self.client.get('/business/calendar/',{'date':day,'view':'day'})
        self.assertEqual(response.context['stats']['cancelled'],1)
        self.assertEqual(response.context['stats']['waitlist'],1)

    def test_calendar_invalid_date_empty_period_and_login(self):
        self.client.force_login(self.owner)
        for value in ['bad','9999-12-31','0001-01-01']:
            response=self.client.get('/business/calendar/',{'date':value})
            self.assertEqual(response.status_code,200)
            self.assertEqual(response.context['selected'],timezone.localdate().isoformat())
        response=self.client.get('/business/calendar/',{'date':'2000-01-01','view':'day'})
        self.assertContains(response,'No appointments.')
        self.assertEqual(response.context['stats']['confirmed'],0)
        self.client.logout(); self.assertEqual(self.client.get('/business/calendar/').status_code,302)

    def test_calendar_toronto_midnight_boundary(self):
        tz=timezone.get_current_timezone()
        midnight=timezone.make_aware(datetime(2030,3,11,0,0),tz)
        inside=self.slot(self.biz,midnight)
        outside=self.slot(self.biz,midnight-timedelta(minutes=1))
        self.client.force_login(self.owner)
        response=self.client.get('/business/calendar/',{'date':'2030-03-11','view':'day'})
        ids=[s.pk for g in response.context['groups'] for s in g['slots']]
        self.assertIn(inside.pk,ids); self.assertNotIn(outside.pk,ids)

    def test_cancelled_target_and_stale_post_keep_original(self):
        self.new.cancelled=True; self.new.save()
        with self.assertRaises(RuleError): reschedule_booking(self.booking.pk,self.new.pk,self.customer)
        self.booking.refresh_from_db(); self.assertTrue(self.booking.active)
