"""Exercise simultaneous writes against a disposable file-backed SQLite database."""
import os
import sys
import tempfile
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from threading import Barrier
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

def main():
    with tempfile.TemporaryDirectory(prefix='slotrelay-concurrency-') as directory:
        os.environ['SLOTRELAY_DATA_DIR']=directory
        os.environ.setdefault('DJANGO_SETTINGS_MODULE','config.settings')
        import django
        django.setup()
        from django.core.management import call_command
        from django.db import connections
        from django.contrib.auth.models import User
        from django.utils import timezone
        from datetime import timedelta
        from booking.models import Business,Slot,Booking
        from booking.services import book,reschedule_booking,RuleError
        call_command('migrate',verbosity=0)
        owner=User.objects.create_user('race-owner')
        a=User.objects.create_user('race-a')
        b=User.objects.create_user('race-b')
        business=Business.objects.create(owner=owner,name='Race test',category='Fitness',city='Test',address='Test',description='Test')
        slot=Slot.objects.create(business=business,service='Concurrent booking',starts=timezone.now()+timedelta(days=1),duration=30,price=10)
        barrier=Barrier(2)
        def attempt(user_id):
            try:
                user=User.objects.get(pk=user_id)
                barrier.wait(timeout=10)
                book(slot.pk,user)
                return 'booked'
            except RuleError:
                return 'rejected'
            finally:
                connections.close_all()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(attempt,[a.pk,b.pk]))
        assert sorted(results)==['booked','rejected'],results
        assert Booking.objects.filter(slot=slot,active=True).count()==1
        connections.close_all()
        original_slot=Slot.objects.create(business=business,service='Reschedule race',starts=timezone.now()+timedelta(days=2),duration=30,price=10)
        target=Slot.objects.create(business=business,service='Reschedule race',starts=timezone.now()+timedelta(days=3),duration=30,price=10)
        original=book(original_slot.pk,a)
        barrier=Barrier(2)
        def race(kind):
            try:
                user=User.objects.get(pk=a.pk if kind=='move' else b.pk)
                barrier.wait(timeout=10)
                if kind=='move': reschedule_booking(original.pk,target.pk,user)
                else: book(target.pk,user)
                return kind
            except RuleError:
                return 'rejected'
            finally:
                connections.close_all()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results=list(pool.map(race,['move','book']))
        assert results.count('rejected')==1,results
        assert Booking.objects.filter(slot=target,active=True).count()==1
        original.refresh_from_db()
        assert original.active == ('book' in results),results
        connections.close_all()
        print('PASS: reschedule versus booking race preserves one target booking and the correct original state.')
        print('PASS: two simultaneous booking attempts, exactly one confirmed booking.')
        from django import forms
        from booking.models import Staff, WaitEntry
        from booking.forms import BatchSlotForm, save_batch
        from booking.services import join_flexible, cancel_booking
        staff = Staff.objects.create(business=business,name='Race staff')
        start = timezone.localtime().replace(hour=10,minute=0,second=0,microsecond=0)+timedelta(days=7)
        barrier = Barrier(2)
        def publish(_):
            try:
                form = BatchSlotForm({'service':'Batch race','staff':staff.pk,'starts':start.strftime('%Y-%m-%dT%H:%M'),'duration':30,'price':20,'count':3,'gap':0},business=business)
                assert form.is_valid(), form.errors
                barrier.wait(timeout=10)
                save_batch(form,business)
                return 'published'
            except forms.ValidationError:
                return 'rejected'
            finally: connections.close_all()
        with ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(publish,[1,2]))
        assert sorted(results)==['published','rejected'], results
        assert Slot.objects.filter(service='Batch race').count()==3
        print('PASS: simultaneous overlapping batches create exactly one complete batch.')
        one = Slot.objects.create(business=business,service='Flex race',starts=start+timedelta(days=2),duration=30,price=20)
        two = Slot.objects.create(business=business,service='Flex race',starts=start+timedelta(days=3),duration=30,price=20)
        reservations = [book(slot.pk,a) for slot in [one,two]]
        group = join_flexible(b,business,'Flex race',one.starts,two.starts,20)
        barrier = Barrier(2)
        def release(pk):
            try:
                barrier.wait(timeout=10)
                cancel_booking(pk,a)
            finally: connections.close_all()
        with ThreadPoolExecutor(max_workers=2) as pool:
            list(pool.map(release,[reservation.pk for reservation in reservations]))
        assert WaitEntry.objects.filter(flexible=group,status='offered').count()==1
        print('PASS: simultaneous cancellations hold only one offer per flexible group.')
        connections.close_all()
if __name__=='__main__': main()
