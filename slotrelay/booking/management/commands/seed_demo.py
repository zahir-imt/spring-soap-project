from datetime import timedelta
from django.core.management.base import BaseCommand
from django.contrib.auth.models import User
from django.utils import timezone
from booking.models import Business, Slot
class Command(BaseCommand):
    help='Create clearly labelled fictional listings, with disabled owner logins. Safe to run more than once.'
    def handle(self,*args,**kwargs):
        rows=[('demo-wellness','Demo · Sunday Studio','Wellness','Restorative yoga',30,25),('demo-beauty','Demo · Common Ground','Beauty','Cut & style',45,55),('demo-learning','Demo · Small Steps','Learning','Beginner guitar lesson',60,40),('demo-fitness','Demo · Motion Club','Fitness','Personal training',45,65),('demo-repairs','Demo · Fix & Flourish','Repairs','Bike tune-up consultation',30,20),('demo-wellness-two','Demo · Quiet Hours','Wellness','Guided meditation',30,18)]
        for index,(username,name,category,service,duration,price) in enumerate(rows):
            user,created=User.objects.get_or_create(username=username)
            if created:
                user.set_unusable_password()
                user.save()
            biz,_=Business.objects.get_or_create(owner=user,defaults={'name':name,'category':category,'city':'Toronto','address':'Fictional demo location — do not attend','description':'A fictional listing for trying SlotRelay. This is not a real business or appointment.'})
            if not Slot.objects.filter(business=biz,starts__gt=timezone.now()).exists():
                start=(timezone.localtime()+timedelta(days=1+index//3)).replace(hour=10+(index%3)*2,minute=0,second=0,microsecond=0)
                Slot.objects.create(business=biz,service=service,starts=start,duration=duration,price=price)
        self.stdout.write(self.style.SUCCESS('Fictional demo listings ready. No login passwords created.'))
