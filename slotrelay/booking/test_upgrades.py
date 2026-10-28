from datetime import timedelta, timezone as dt_timezone
from django import forms
from django.contrib.auth.models import User
from django.test import TestCase, Client
from django.utils import timezone
from .models import Business, Staff, Slot, Booking, WaitEntry, FlexibleWaitlist, Notice
from .forms import SlotForm, BatchSlotForm, save_slot, save_batch, create_slot, FlexibleWaitlistForm
from .services import book, join_waitlist, cancel_booking, join_flexible, close_flexible, refresh_offers, RuleError
from .upgrade_views import ical_text, ical_fold

class UpgradeTests(TestCase):
    def setUp(self):
        self.owner = User.objects.create_user('owner')
        self.other = User.objects.create_user('other')
        self.customer = User.objects.create_user('customer')
        self.waiter = User.objects.create_user('waiter')
        self.biz = Business.objects.create(owner=self.owner,name='Studio',category='Wellness',city='Toronto',address='Main, Street',description='Demo')
        self.otherbiz = Business.objects.create(owner=self.other,name='Other',category='Wellness',city='Toronto',address='Elsewhere',description='Demo')
        self.a = Staff.objects.create(business=self.biz,name='Alex')
        self.b = Staff.objects.create(business=self.biz,name='Blair')
        self.foreign = Staff.objects.create(business=self.otherbiz,name='Private staff')
        self.start = timezone.localtime().replace(hour=10,minute=0,second=0,microsecond=0)+timedelta(days=14)
        self.client.force_login(self.owner)
    def values(self, **kwargs):
        return dict(service='Yoga', starts=self.start, duration=30, price=50, staff=self.a, **kwargs)
    def slot(self, offset=0, staff=None, **kwargs):
        values = self.values()
        values.update(starts=self.start+timedelta(minutes=offset),staff=staff or self.a,**kwargs)
        return create_slot(self.biz,**values)
    def data(self, **kwargs):
        values={'service':'Yoga','starts':self.start.strftime('%Y-%m-%dT%H:%M'),'duration':30,'price':50,'staff':self.a.pk,'count':3,'gap':10}
        values.update(kwargs)
        return values
    def busy_group(self):
        first, second = self.slot(), self.slot(60)
        bookings=[book(s.pk,self.other) for s in [first,second]]
        group=join_flexible(self.customer,self.biz,'Yoga',self.start,self.start+timedelta(hours=2),50)
        return first,second,bookings,group
    def test_different_staff_can_overlap(self):
        self.slot()
        self.slot(staff=self.b)
        self.assertEqual(Slot.objects.count(),2)
    def test_same_staff_overlap_rejected_but_boundary_allowed(self):
        self.slot()
        with self.assertRaises(forms.ValidationError): self.slot(15)
        self.slot(30)
        self.assertEqual(Slot.objects.count(),2)
    def test_unassigned_reserves_whole_business_in_both_directions(self):
        self.slot()
        with self.assertRaises(forms.ValidationError): create_slot(self.biz,**dict(self.values(),staff=None))
        create_slot(self.biz,**dict(self.values(),staff=None,starts=self.start+timedelta(hours=3)))
        with self.assertRaises(forms.ValidationError): self.slot(180,staff=self.b)
    def test_foreign_and_inactive_staff_cannot_be_assigned(self):
        form=SlotForm(self.data(staff=self.foreign.pk),business=self.biz)
        self.assertFalse(form.is_valid())
        with self.assertRaises(forms.ValidationError): create_slot(self.biz,**dict(self.values(),staff=self.foreign))
        self.a.active=False; self.a.save()
        with self.assertRaises(forms.ValidationError): self.slot()
    def test_batch_creates_spaced_appointments(self):
        form=BatchSlotForm(self.data(),business=self.biz)
        self.assertTrue(form.is_valid(),form.errors)
        result=save_batch(form,self.biz)
        self.assertEqual([s.starts for s in result],[self.start+timedelta(minutes=n*40) for n in range(3)])
    def test_batch_conflict_rolls_back_every_new_slot(self):
        self.slot(80)
        form=BatchSlotForm(self.data(),business=self.biz)
        self.assertTrue(form.is_valid())
        with self.assertRaises(forms.ValidationError): save_batch(form,self.biz)
        self.assertEqual(Slot.objects.count(),1)
    def test_batch_limit_and_timezone_validation(self):
        self.assertFalse(BatchSlotForm(self.data(count=51),business=self.biz).is_valid())
        self.assertFalse(BatchSlotForm(self.data(starts='2027-03-14T02:30'),business=self.biz).is_valid())
    def test_staff_management_is_scoped_and_preserves_bookings(self):
        slot=self.slot(); booking=book(slot.pk,self.customer)
        self.assertEqual(self.client.post(f'/business/staff/{self.foreign.pk}/toggle/').status_code,404)
        self.assertEqual(self.client.post(f'/business/staff/{self.a.pk}/toggle/').status_code,302)
        self.a.refresh_from_db(); booking.refresh_from_db()
        self.assertFalse(self.a.active); self.assertTrue(booking.active)
        response=self.client.post('/business/staff/',{'name':'Alex'})
        self.assertEqual(response.status_code,200)
        self.assertContains(response,'already exists')
    def test_all_new_screens_render(self):
        for path in ['/business/staff/','/business/batch/','/business/new-slot/','/notifications/','/dashboard/','/business/calendar/','/waitlist/flexible/']:
            with self.subTest(path=path): self.assertEqual(self.client.get(path).status_code,200)
    def test_flexible_group_one_offer_and_accept_closes_siblings(self):
        first,second,bookings,group=self.busy_group()
        for booking in bookings: cancel_booking(booking.pk,self.other)
        self.assertEqual(WaitEntry.objects.filter(flexible=group,status='offered').count(),1)
        offer=WaitEntry.objects.get(flexible=group,status='offered')
        book(offer.slot_id,self.customer)
        group.refresh_from_db()
        self.assertFalse(group.active)
        self.assertFalse(WaitEntry.objects.filter(flexible=group,status__in=['waiting','offered']).exists())
        self.assertEqual(Booking.objects.filter(customer=self.customer,active=True).count(),1)
    def test_flexible_queue_does_not_jump_existing_waiter(self):
        slot=self.slot(); booking=book(slot.pk,self.other)
        join_waitlist(slot.pk,self.waiter)
        group=join_flexible(self.customer,self.biz,'Yoga',self.start,self.start,50)
        cancel_booking(booking.pk,self.other)
        self.assertEqual(WaitEntry.objects.get(slot=slot,status='offered').customer,self.waiter)
        self.assertEqual(WaitEntry.objects.get(flexible=group).status,'waiting')
    def test_flexible_filters_price_time_service_and_skips_available(self):
        wanted=self.slot(); book(wanted.pk,self.other)
        self.slot(60,price=75); self.slot(120,service='Massage'); self.slot(180)
        group=join_flexible(self.customer,self.biz,'Yoga',self.start,self.start+timedelta(hours=3),50)
        self.assertEqual(list(WaitEntry.objects.filter(flexible=group).values_list('slot_id',flat=True)),[wanted.pk])
    def test_flexible_close_releases_offer_to_next_customer(self):
        first,second,bookings,group=self.busy_group()
        join_waitlist(first.pk,self.waiter)
        cancel_booking(bookings[0].pk,self.other)
        close_flexible(group.pk,self.customer)
        self.assertEqual(WaitEntry.objects.get(slot=first,status='offered').customer,self.waiter)
        self.assertFalse(WaitEntry.objects.filter(flexible=group,status__in=['waiting','offered']).exists())
    def test_flexible_expiry_moves_to_another_match(self):
        first,second,bookings,group=self.busy_group()
        for booking in bookings: cancel_booking(booking.pk,self.other)
        offer=WaitEntry.objects.get(flexible=group,status='offered')
        offer.expires=timezone.now()-timedelta(seconds=1); offer.save()
        refresh_offers()
        self.assertEqual(WaitEntry.objects.filter(flexible=group,status='offered').count(),1)
        self.assertNotEqual(WaitEntry.objects.get(flexible=group,status='offered').pk,offer.pk)
    def test_flexible_ownership_duplicates_and_invalid_window(self):
        first,second,bookings,group=self.busy_group()
        with self.assertRaises(RuleError): join_flexible(self.customer,self.biz,'Yoga',self.start,self.start+timedelta(hours=2),50)
        with self.assertRaises(RuleError): join_flexible(self.owner,self.biz,'Yoga',self.start,self.start,50)
        with self.assertRaises(RuleError): join_flexible(self.waiter,self.biz,'Yoga',self.start,self.start+timedelta(days=32),50)
        self.assertEqual(self.client.post(f'/waitlist/flexible/{group.pk}/close/').status_code,404)
    def test_notifications_are_private_paginated_and_counted(self):
        Notice.objects.bulk_create([Notice(user=self.customer,text=f'Customer update {n}') for n in range(25)])
        private=Notice.objects.create(user=self.other,text='Private other update')
        self.client.force_login(self.customer)
        response=self.client.get('/notifications/')
        self.assertEqual(response.context['unread_count'],25)
        self.assertEqual(len(response.context['page']),20)
        self.assertNotContains(response,'Private other update')
        self.assertEqual(self.client.post(f'/notifications/{private.pk}/read/').status_code,404)
        self.client.post('/notifications/read-all/')
        self.assertEqual(self.client.get('/notifications/?filter=unread').context['unread_count'],0)
        private.refresh_from_db(); self.assertIsNone(private.read_at)
    def test_mark_one_read_is_idempotent_and_get_cannot_mutate(self):
        notice=Notice.objects.create(user=self.owner,text='Hello')
        self.assertEqual(self.client.get(f'/notifications/{notice.pk}/read/').status_code,405)
        self.client.post(f'/notifications/{notice.pk}/read/')
        notice.refresh_from_db(); stamp=notice.read_at
        self.client.post(f'/notifications/{notice.pk}/read/')
        notice.refresh_from_db(); self.assertEqual(notice.read_at,stamp)
    def test_calendar_export_is_private_utc_and_has_duration(self):
        booking=book(self.slot().pk,self.customer)
        self.assertEqual(self.client.get(f'/bookings/{booking.pk}/calendar.ics').status_code,404)
        self.client.force_login(self.customer)
        response=self.client.get(f'/bookings/{booking.pk}/calendar.ics')
        body=response.content.decode()
        stamp=lambda v:v.astimezone(dt_timezone.utc).strftime('%Y%m%dT%H%M%SZ')
        self.assertIn('DTSTART:'+stamp(self.start),body)
        self.assertIn('DTEND:'+stamp(self.start+timedelta(minutes=30)),body)
        self.assertIn('STATUS:CONFIRMED',body)
        self.assertIn('LOCATION:Main\\, Street\\, Toronto',body)
        self.assertEqual(response['Cache-Control'],'private, no-store')
        self.assertTrue(all(len(line.encode())<=75 for line in body.split('\r\n')))
        cancel_booking(booking.pk,self.customer)
        self.assertIn(b'STATUS:CANCELLED',self.client.get(f'/bookings/{booking.pk}/calendar.ics').content)
    def test_ics_injection_and_unicode_folding(self):
        self.assertEqual(ical_text('Hello\r\nBEGIN:BAD;,'),'Hello\\nBEGIN:BAD\\;\\,')
        text='SUMMARY:'+'é'*100
        folded=ical_fold(text)
        self.assertEqual(folded.replace('\r\n ',''),text)
        self.assertTrue(all(len(line.encode())<=75 for line in folded.split('\r\n')))
    def test_new_mutations_require_csrf_and_auth(self):
        client=Client(enforce_csrf_checks=True); client.force_login(self.owner)
        for path in ['/business/staff/','/business/batch/',f'/business/staff/{self.a.pk}/toggle/','/waitlist/flexible/','/notifications/read-all/']:
            with self.subTest(path=path): self.assertEqual(client.post(path,{}).status_code,403)
        self.client.logout()
        self.assertEqual(self.client.get('/notifications/').status_code,302)

    def test_flexible_group_is_visible_and_exhausted_group_closes(self):
        first,second,bookings,group=self.busy_group()
        self.client.force_login(self.customer)
        response=self.client.get('/dashboard/')
        self.assertContains(response,'Close this flexible waitlist')
        self.assertEqual(list(response.context['flexible_groups']),[group])
        WaitEntry.objects.filter(flexible=group).update(status='expired')
        refresh_offers()
        group.refresh_from_db(); self.assertFalse(group.active)
    def test_batch_across_daylight_saving_uses_elapsed_minutes(self):
        from datetime import datetime
        from zoneinfo import ZoneInfo
        start=datetime(2027,3,14,1,30,tzinfo=ZoneInfo('America/Toronto'))
        form=BatchSlotForm(self.data(starts=start.strftime('%Y-%m-%dT%H:%M'),gap=0),business=self.biz)
        self.assertTrue(form.is_valid(),form.errors)
        slots=save_batch(form,self.biz)
        self.assertEqual([timezone.localtime(slot.starts).hour for slot in slots],[1,3,3])
        self.assertEqual((slots[1].starts-slots[0].starts).total_seconds(),1800)
