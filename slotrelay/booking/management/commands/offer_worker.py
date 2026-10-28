import time
from django.core.management.base import BaseCommand
from booking.services import refresh_offers
class Command(BaseCommand):
    help='Advance expired waitlist offers every 30 seconds; no external messages are sent.'
    def add_arguments(self,parser): parser.add_argument('--once',action='store_true')
    def handle(self,*args,**options):
        while True:
            refresh_offers()
            if options['once']: return
            time.sleep(30)
