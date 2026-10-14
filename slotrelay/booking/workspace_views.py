from datetime import date, datetime, time, timedelta
from django import forms
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.db.models import Exists, OuterRef, Prefetch, Sum
from django.shortcuts import get_object_or_404, redirect, render
from django.utils import timezone
from django.views.decorators.http import require_http_methods
from .models import Booking, Business, Slot, WaitEntry
from .services import refresh_offers, reschedule_booking, RuleError

class AppointmentChoice(forms.ModelChoiceField):
    def label_from_instance(self, slot):
        start = timezone.localtime(slot.starts).strftime('%a, %b %d, %Y at %I:%M %p')
        return f'{start} — ${slot.price} CAD — {slot.duration} minutes'

class RescheduleForm(forms.Form):
    appointment = AppointmentChoice(queryset=Slot.objects.none(), empty_label='Choose a new time', label='New appointment (Toronto time)')

@login_required
@require_http_methods(['GET', 'POST'])
def reschedule(request, pk):
    booking = get_object_or_404(Booking.objects.select_related('slot__business'), pk=pk, customer=request.user)
    if not booking.active or booking.slot.cancelled or booking.slot.starts <= timezone.now():
        messages.error(request, 'Only a confirmed future appointment can be rescheduled.')
        return redirect('dashboard')
    refresh_offers()
    source = booking.slot
    choices = Slot.objects.filter(business=source.business, service=source.service, duration=source.duration, starts__gt=timezone.now(), cancelled=False).exclude(pk=source.pk).annotate(taken=Exists(Booking.objects.filter(slot=OuterRef('pk'), active=True)), reserved=Exists(WaitEntry.objects.filter(slot=OuterRef('pk'), status='offered').exclude(customer=request.user))).filter(taken=False, reserved=False)
    form = RescheduleForm(request.POST if request.method == 'POST' else None)
    form.fields['appointment'].queryset = choices
    if request.method == 'POST' and form.is_valid():
        try:
            reschedule_booking(booking.pk, form.cleaned_data['appointment'].pk, request.user)
        except RuleError as exc:
            form.add_error(None, str(exc))
        else:
            messages.success(request, 'Appointment rescheduled. Your original time has been released.')
            return redirect('dashboard')
    return render(request, 'reschedule.html', {'form': form, 'booking': booking, 'has_choices': choices.exists()})

@login_required
def business_calendar(request):
    business = Business.objects.filter(owner=request.user).first()
    if not business:
        return redirect('business-edit')
    refresh_offers()
    today = timezone.localdate()
    try:
        selected = date.fromisoformat(request.GET.get('date', today.isoformat()))
        if not 1900 <= selected.year <= 2100:
            raise ValueError
    except (ValueError, TypeError):
        selected = today
        messages.info(request, 'Choose a valid date between 1900 and 2100. Showing today instead.')
    mode = 'day' if request.GET.get('view') == 'day' else 'week'
    start = selected if mode == 'day' else selected - timedelta(days=selected.weekday())
    days = 1 if mode == 'day' else 7
    end = start + timedelta(days=days)
    tz = timezone.get_current_timezone()
    lower = timezone.make_aware(datetime.combine(start, time.min), tz)
    upper = timezone.make_aware(datetime.combine(end, time.min), tz)
    slots = Slot.objects.filter(business=business, starts__gte=lower, starts__lt=upper).prefetch_related(Prefetch('booking_set', queryset=Booking.objects.filter(active=True).select_related('customer'), to_attr='confirmed')).annotate(held=Exists(WaitEntry.objects.filter(slot=OuterRef('pk'), status='offered')))
    groups = [{'date': start + timedelta(days=i), 'slots': []} for i in range(days)]
    for slot in slots:
        groups[(timezone.localdate(slot.starts) - start).days]['slots'].append(slot)
    records = Booking.objects.filter(slot__business=business, slot__starts__gte=lower, slot__starts__lt=upper)
    offered = WaitEntry.objects.filter(slot__business=business, slot__starts__gte=lower, slot__starts__lt=upper, status='accepted')
    stats = {'confirmed': records.filter(active=True).count(), 'cancelled': records.filter(active=False, rescheduled_to__isnull=True).count(), 'rescheduled': records.filter(rescheduled_to__isnull=False).count(), 'waitlist': offered.count(), 'value': records.filter(active=True).aggregate(total=Sum('slot__price'))['total'] or 0}
    return render(request, 'calendar.html', {'business': business, 'groups': groups, 'stats': stats, 'selected': selected.isoformat(), 'mode': mode, 'start': start, 'last': end - timedelta(days=1), 'previous': (selected-timedelta(days=days)).isoformat(), 'next': (selected+timedelta(days=days)).isoformat(), 'today': today.isoformat(), 'now': timezone.now()})
