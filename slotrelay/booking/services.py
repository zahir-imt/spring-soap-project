from datetime import timedelta
from django.db import transaction
from django.utils import timezone
from .models import Slot, Booking, WaitEntry, Notice, FlexibleWaitlist

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
        blocked_groups = WaitEntry.objects.filter(status='offered', expires__gt=now, flexible__isnull=False).values_list('flexible_id',flat=True)
        offer = WaitEntry.objects.filter(slot=slot,status='waiting').exclude(flexible_id__in=blocked_groups).first()
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
    live_groups = WaitEntry.objects.filter(status__in=['waiting','offered'],flexible__isnull=False).values_list('flexible_id',flat=True)
    FlexibleWaitlist.objects.filter(active=True).exclude(pk__in=live_groups).update(active=False)

@transaction.atomic
def book(slot_id, user):
    slot = Slot.objects.select_for_update().get(pk=slot_id)
    if slot.cancelled or slot.starts <= timezone.now(): raise RuleError('This appointment is no longer available.')
    if slot.business.owner_id == user.id: raise RuleError('You cannot book your own business.')
    if Booking.objects.filter(slot=slot,active=True).exists(): raise RuleError('This appointment has already been booked. Join the waitlist instead.')
    offer = rotate(slot)
    if offer and offer.customer_id != user.id: raise RuleError('This opening is temporarily reserved for the next person on the waitlist.')
    result = Booking.objects.create(slot=slot,customer=user)
    group_ids = list(WaitEntry.objects.filter(slot=slot,customer=user,status__in=['waiting','offered'],flexible__isnull=False).values_list('flexible_id',flat=True))
    WaitEntry.objects.filter(slot=slot,customer=user,status__in=['waiting','offered']).update(status='accepted')
    FlexibleWaitlist.objects.filter(pk__in=group_ids).update(active=False)
    WaitEntry.objects.filter(flexible_id__in=group_ids,status__in=['waiting','offered']).update(status='left')
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

@transaction.atomic
def reschedule_booking(booking_id, target_id, user):
    original = Booking.objects.select_for_update().select_related('slot').get(pk=booking_id, customer=user)
    slots = {s.pk: s for s in Slot.objects.select_for_update().filter(pk__in=[original.slot_id, target_id]).order_by('pk')}
    source = slots[original.slot_id]
    target = slots.get(target_id)
    if not original.active or source.cancelled or source.starts <= timezone.now():
        raise RuleError('Only a confirmed future appointment can be rescheduled.')
    if not target or target.pk == source.pk:
        raise RuleError('Choose a different available appointment.')
    if (target.business_id, target.service, target.duration) != (source.business_id, source.service, source.duration):
        raise RuleError('Choose the same service and duration at the same business.')
    # Reserve the replacement first. Any failure rolls back the whole change.
    replacement = book(target.pk, user)
    original.active = False
    original.rescheduled_to = replacement
    original.save(update_fields=['active', 'rescheduled_to'])
    rotate(source)
    notify(user, f'Your appointment for {source.service} was rescheduled. See your new time in My appointments.')
    notify(source.business.owner, f'A customer rescheduled {source.service}. The original time is available to the waitlist.')
    return replacement

@transaction.atomic
def join_flexible(user, business, service, earliest, latest, max_price):
    if business.owner_id == user.pk: raise RuleError('You cannot join your own business waitlist.')
    if earliest <= timezone.now() or latest < earliest or latest-earliest > timedelta(days=31) or max_price < 0:
        raise RuleError('Choose a future window of up to 31 days and a nonnegative price.')
    if FlexibleWaitlist.objects.filter(customer=user,active=True).count() >= 10:
        raise RuleError('You can have up to 10 active flexible waitlists. Close one first.')
    candidates = list(Slot.objects.select_for_update().filter(business=business,service=service,cancelled=False,starts__gte=earliest,starts__lte=latest,price__lte=max_price).order_by('pk')[:101])
    if len(candidates) > 100: raise RuleError('Too many matches. Narrow your time window.')
    eligible = []
    for slot in candidates:
        rotate(slot)
        if Booking.objects.filter(slot=slot,customer=user,active=True).exists(): continue
        if WaitEntry.objects.filter(slot=slot,customer=user,status__in=['waiting','offered']).exists(): continue
        if Booking.objects.filter(slot=slot,active=True).exists() or WaitEntry.objects.filter(slot=slot,status='offered').exists():
            eligible.append(slot)
    if not eligible: raise RuleError('No matching busy appointments to join. Available openings can be booked directly, and existing waitlist entries are unchanged.')
    group = FlexibleWaitlist.objects.create(customer=user,business=business,service=service,earliest=earliest,latest=latest,max_price=max_price)
    for slot in eligible: WaitEntry.objects.create(slot=slot,customer=user,flexible=group)
    notify(user, f'Flexible waitlist joined: {len(eligible)} current appointments for {service}. Accepting one closes the other entries in this group.')
    return group

@transaction.atomic
def close_flexible(pk, user):
    group = FlexibleWaitlist.objects.select_for_update().get(pk=pk,customer=user)
    ids = list(WaitEntry.objects.filter(flexible=group,status__in=['waiting','offered']).values_list('slot_id',flat=True))
    group.active = False
    group.save(update_fields=['active'])
    WaitEntry.objects.filter(flexible=group,status__in=['waiting','offered']).update(status='left')
    for slot in Slot.objects.select_for_update().filter(pk__in=ids).order_by('pk'): rotate(slot)
