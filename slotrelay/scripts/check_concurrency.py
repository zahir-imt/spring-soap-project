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
if __name__=='__main__': main()
