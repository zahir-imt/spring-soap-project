from datetime import timedelta
from django.db import transaction
from django.utils import timezone
from .models import Slot, Booking, WaitEntry, Notice

class RuleError(Exception): pass

def notify(user, text):
    Notice.objects.create(user=user, text=text)

def rotate(slot):
    now = timezone.now()
    if slot.cancelled or slot.starts <= now:
        WaitEntry.objects.filter(slot=slot,status__in=['waiting','offered']).update(status='expired')
        return None
    WaitEntry.objects.filter(slot=slot,status='offered',expires__lte=now).update(status='expired')
    if Booking.objects.filter(slot=slot,active=True).exists(): return None
    offer = WaitEntry.objects.filter(slot=slot,status='offered').first()
    if not offer:
        offer = WaitEntry.objects.filter(slot=slot,status='waiting').first()
        if offer:
            offer.status = 'offered'
            offer.expires = min(now + timedelta(minutes=15), slot.starts)
            offer.save()
            notify(offer.customer, f'An opening is yours to claim: {slot.service}. Accept it from your dashboard within 15 minutes (or before the appointment starts).')
    return offer

@transaction.atomic
def refresh_offers():
    ids = WaitEntry.objects.filter(status__in=['waiting','offered']).values_list('slot_id',flat=True).distinct()
    for slot in Slot.objects.select_for_update().filter(pk__in=ids).order_by('pk'):
        rotate(slot)

@transaction.atomic
def book(slot_id, user):
    slot = Slot.objects.select_for_update().get(pk=slot_id)
    if slot.cancelled or slot.starts <= timezone.now(): raise RuleError('This appointment is no longer available.')
    if slot.business.owner_id == user.id: raise RuleError('You cannot book your own business.')
    if Booking.objects.filter(slot=slot,active=True).exists(): raise RuleError('This appointment has already been booked. Join the waitlist instead.')
    offer = rotate(slot)
    if offer and offer.customer_id != user.id: raise RuleError('This opening is temporarily reserved for the next person on the waitlist.')
    result = Booking.objects.create(slot=slot,customer=user)
    WaitEntry.objects.filter(slot=slot,customer=user,status__in=['waiting','offered']).update(status='accepted')
    notify(user, f'Booking confirmed: {slot.service} at {slot.business.name}.')
    notify(slot.business.owner, f'A customer booked {slot.service}. See your business dashboard for details.')
    return result

@transaction.atomic
def join_waitlist(slot_id, user):
    slot = Slot.objects.select_for_update().get(pk=slot_id)
    if slot.cancelled or slot.starts <= timezone.now(): raise RuleError('This appointment is no longer available.')
    if slot.business.owner_id == user.id: raise RuleError('You cannot join your own business waitlist.')
    if Booking.objects.filter(slot=slot,customer=user,active=True).exists(): raise RuleError('You already booked this appointment.')
    if WaitEntry.objects.filter(slot=slot,customer=user,status__in=['waiting','offered']).exists(): raise RuleError('You are already on this waitlist.')
    rotate(slot)
    if not Booking.objects.filter(slot=slot,active=True).exists() and not WaitEntry.objects.filter(slot=slot,status='offered').exists(): raise RuleError('This appointment is available. Book it directly.')
    WaitEntry.objects.create(slot=slot,customer=user)
    notify(user, f'You joined the waitlist for {slot.service}. Check your dashboard for offers.')

@transaction.atomic
def cancel_booking(booking_id, user):
    booking = Booking.objects.select_related('slot').get(pk=booking_id,customer=user)
    slot = Slot.objects.select_for_update().get(pk=booking.slot_id)
    if slot.starts <= timezone.now(): raise RuleError('Past appointments cannot be cancelled.')
    if not booking.active: return
    booking.active = False
    booking.save()
    notify(user, f'Your booking for {slot.service} was cancelled.')
    notify(slot.business.owner, f'A booking for {slot.service} was cancelled.')
    rotate(slot)

@transaction.atomic
def leave_waitlist(entry_id, user):
    entry = WaitEntry.objects.get(pk=entry_id,customer=user)
    slot = Slot.objects.select_for_update().get(pk=entry.slot_id)
    if entry.status in ['waiting','offered']:
        entry.status = 'left'
        entry.save()
        rotate(slot)

@transaction.atomic
def cancel_slot(slot_id, user):
    slot = Slot.objects.select_for_update().get(pk=slot_id,business__owner=user)
    if slot.starts <= timezone.now(): raise RuleError('Past appointments cannot be cancelled.')
    for booking in Booking.objects.filter(slot=slot,active=True):
        notify(booking.customer, f'{slot.business.name} cancelled {slot.service}. Your booking is cancelled.')
    for entry in WaitEntry.objects.filter(slot=slot,status__in=['waiting','offered']):
        notify(entry.customer, f'{slot.business.name} cancelled {slot.service}. The waitlist is closed.')
    Booking.objects.filter(slot=slot,active=True).update(active=False)
    WaitEntry.objects.filter(slot=slot,status__in=['waiting','offered']).update(status='expired')
    slot.cancelled = True
    slot.save()
