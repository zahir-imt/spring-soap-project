from datetime import timedelta, datetime
from decimal import Decimal
from zoneinfo import ZoneInfo
from django.contrib.auth.models import User
from django.test import TestCase
from django.utils import timezone
from .models import Business, Slot, Booking, WaitEntry

class DiscoveryTests(TestCase):
    def setUp(self):
        owner=User.objects.create_user('owner')
        self.customer=User.objects.create_user('customer')
        self.biz=Business.objects.create(owner=owner,name='Discovery Studio',category='Fitness',city='Toronto',address='Test',description='Test')
        self.start=timezone.localtime().replace(hour=10,minute=0,second=0,microsecond=0)+timedelta(days=14)
        self.slots=[Slot.objects.create(business=self.biz,service='Yoga',starts=self.start+timedelta(hours=i),duration=30,price=i) for i in range(30)]
    def results(self,params): return self.client.get('/',params)
    def test_page_reaches_results_beyond_first_page_with_stable_order(self):
        first=self.results({}); second=self.results({'page':2})
        self.assertEqual(len(first.context['slots']),24)
        self.assertEqual(len(second.context['slots']),6)
        self.assertEqual(first.context['page'].paginator.count,30)
        self.assertEqual(list(second.context['slots'])[0].pk,self.slots[24].pk)
    def test_budget_zero_and_price_sort(self):
        response=self.results({'max_price':'0'})
        self.assertEqual([s.price for s in response.context['slots']],[Decimal('0')])
        response=self.results({'max_price':'5','sort':'-price'})
        self.assertEqual([s.price for s in response.context['slots']],list(map(Decimal,range(5,-1,-1))))
    def test_available_excludes_booked_and_active_offers(self):
        Booking.objects.create(slot=self.slots[0],customer=self.customer)
        WaitEntry.objects.create(slot=self.slots[1],customer=self.customer,status='offered',expires=timezone.now()+timedelta(minutes=10))
        response=self.results({'availability':'open'})
        self.assertEqual(response.context['page'].paginator.count,28)
        response=self.results({'availability':'waitlist'})
        self.assertEqual({s.pk for s in response.context['slots']},{self.slots[0].pk,self.slots[1].pk})
    def test_dates_use_toronto_and_inclusive_end(self):
        date=(self.start+timedelta(days=3)).date()
        late=timezone.make_aware(datetime.combine(date,datetime.min.time()).replace(hour=23,minute=30),ZoneInfo('America/Toronto'))
        wanted=Slot.objects.create(business=self.biz,service='Night',starts=late,duration=30,price=10)
        response=self.results({'from_date':date.isoformat(),'to_date':date.isoformat()})
        self.assertEqual([s.pk for s in response.context['slots']],[wanted.pk])
    def test_invalid_filters_show_errors_and_no_broad_results(self):
        for params in [{'max_price':'-1'},{'max_price':'NaN'},{'from_date':'bad'},{'from_date':'2028-02-02','to_date':'2028-02-01'},{'sort':'sql'},{'category':'invalid'}]:
            with self.subTest(params=params):
                response=self.results(params)
                self.assertEqual(response.status_code,200)
                self.assertTrue(response.context['search_form'].errors)
                self.assertEqual(response.context['page'].paginator.count,0)
    def test_business_search_and_pagination_preserve_filters(self):
        response=self.results({'q':'Discovery','city':'Toronto','max_price':'100'})
        self.assertEqual(response.context['page'].paginator.count,30)
        self.assertContains(response,'q=Discovery&amp;city=Toronto&amp;max_price=100&amp;page=2')
    def test_cancelled_and_past_appointments_excluded(self):
        self.slots[0].cancelled=True; self.slots[0].save()
        self.slots[1].starts=timezone.now()-timedelta(days=1); self.slots[1].save()
        self.assertEqual(self.results({}).context['page'].paginator.count,28)
    def test_bad_page_is_safe(self):
        self.assertEqual(self.results({'page':'nonsense'}).context['page'].number,1)
        self.assertEqual(self.results({'page':'999'}).context['page'].number,2)
