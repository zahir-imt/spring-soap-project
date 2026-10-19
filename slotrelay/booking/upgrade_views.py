from datetime import timedelta, timezone as dt_timezone
from django import forms
from django.contrib import messages
from django.contrib.auth.decorators import login_required
from django.core.paginator import Paginator
from django.db import transaction
from django.http import HttpResponse, Http404
from django.shortcuts import render, redirect, get_object_or_404
from django.utils import timezone
from django.views.decorators.http import require_POST
from .models import Business, Staff, Booking, Notice, FlexibleWaitlist
from .forms import StaffForm, BatchSlotForm, save_batch, FlexibleWaitlistForm
from .services import join_flexible, close_flexible, RuleError

@login_required
def staff(request):
    business = get_object_or_404(Business, owner=request.user)
    form = StaffForm(request.POST or None)
    if request.method == 'POST' and form.is_valid():
        with transaction.atomic():
            Business.objects.select_for_update().get(pk=business.pk)
            obj = form.save(commit=False)
            obj.business = business
            try: obj.full_clean()
            except forms.ValidationError as error: form.add_error(None,error)
            else:
                obj.save()
                messages.success(request,'Staff member added. You can now assign their appointments.')
                return redirect('staff')
    return render(request,'staff.html',{'form':form,'staff_members':Staff.objects.filter(business=business)})

@login_required
@require_POST
def staff_toggle(request, pk):
    with transaction.atomic():
        business = get_object_or_404(Business,owner=request.user)
        Business.objects.select_for_update().get(pk=business.pk)
        member = get_object_or_404(Staff,business=business,pk=pk)
        member.active = not member.active
        member.save(update_fields=['active'])
    messages.success(request,'Staff availability updated. Existing appointments and bookings are unchanged.')
    return redirect('staff')

@login_required
def batch(request):
    business = get_object_or_404(Business,owner=request.user)
    form = BatchSlotForm(request.POST or None,business=business)
    if request.method == 'POST' and form.is_valid():
        try: slots = save_batch(form,business)
        except forms.ValidationError as error: form.add_error(None,error)
        else:
            messages.success(request,f'{len(slots)} appointments published.')
            return redirect('business')
    return render(request,'form.html',{'form':form,'title':'Fill your calendar in one step.','eyebrow':'BATCH PUBLISH','button':'Publish all appointments','note':'Publish 2–50 consecutive appointments, with optional breaks. All times use Toronto time. If any appointment conflicts, none are created. Unassigned appointments reserve the whole business.'})

@login_required
def flexible(request):
    form = FlexibleWaitlistForm(request.POST or None,user=request.user)
    if request.method == 'POST' and form.is_valid():
        try: join_flexible(request.user,**form.cleaned_data)
        except RuleError as error: form.add_error(None,str(error))
        else:
            messages.success(request,'Your flexible waitlist is ready. Check My appointments for offers.')
            return redirect('dashboard')
    return render(request,'form.html',{'form':form,'title':'More options. One appointment.','eyebrow':'FLEXIBLE WAITLIST','button':'Join matching waitlists','note':'Join currently listed busy appointments for one service, within your time window and budget. Any staff member may match. This does not watch newly published appointments. Existing queue positions are respected. One offer is held at a time; accepting it closes this group’s other entries. Nothing is booked automatically.'})

@login_required
@require_POST
def flexible_close(request, pk):
    try: close_flexible(pk,request.user)
    except FlexibleWaitlist.DoesNotExist: raise Http404
    messages.success(request,'Flexible waitlist closed.')
    return redirect('dashboard')

@login_required
def inbox(request):
    notices = Notice.objects.filter(user=request.user).order_by('-created','-pk')
    if request.GET.get('filter') == 'unread': notices = notices.filter(read_at__isnull=True)
    return render(request,'inbox.html',{'page':Paginator(notices,20).get_page(request.GET.get('page')),'unread_only':request.GET.get('filter')=='unread'})

@login_required
@require_POST
def notice_read(request, pk):
    notice = get_object_or_404(Notice,pk=pk,user=request.user)
    Notice.objects.filter(pk=notice.pk,user=request.user,read_at__isnull=True).update(read_at=timezone.now())
    return redirect('inbox')

@login_required
@require_POST
def notices_read(request):
    Notice.objects.filter(user=request.user,read_at__isnull=True).update(read_at=timezone.now())
    return redirect('inbox')

def ical_text(value):
    return str(value).replace('\\','\\\\').replace('\r\n','\n').replace('\r','\n').replace('\n','\\n').replace(';','\\;').replace(',','\\,')

def ical_fold(line):
    chunks, current = [], ''
    for char in line:
        if len((current+char).encode('utf-8')) > 75:
            chunks.append(current)
            current = ' '
        current += char
    chunks.append(current)
    return '\r\n'.join(chunks)

@login_required
def calendar_export(request, pk):
    booking = get_object_or_404(Booking.objects.select_related('slot__business','slot__staff'),pk=pk,customer=request.user)
    slot = booking.slot
    stamp = lambda value: value.astimezone(dt_timezone.utc).strftime('%Y%m%dT%H%M%SZ')
    description = f'{slot.duration} minutes. CAD {slot.price}. Staff: {slot.staff.name if slot.staff else "Unassigned"}. Check SlotRelay for changes; this file does not update automatically.'
    lines = ['BEGIN:VCALENDAR','VERSION:2.0','PRODID:-//SlotRelay//Appointments//EN','CALSCALE:GREGORIAN','BEGIN:VEVENT',f'UID:booking-{booking.pk}@slotrelay',f'DTSTAMP:{stamp(timezone.now())}',f'DTSTART:{stamp(slot.starts)}',f'DTEND:{stamp(slot.starts+timedelta(minutes=slot.duration))}',f'SUMMARY:{ical_text(slot.service + " at " + slot.business.name)}',f'LOCATION:{ical_text(slot.business.address + ", " + slot.business.city)}',f'DESCRIPTION:{ical_text(description)}','STATUS:'+('CONFIRMED' if booking.active and not slot.cancelled else 'CANCELLED'),'END:VEVENT','END:VCALENDAR']
    response = HttpResponse('\r\n'.join(ical_fold(line) for line in lines)+'\r\n',content_type='text/calendar; charset=utf-8')
    response['Content-Disposition'] = f'attachment; filename="slotrelay-{booking.pk}.ics"'
    response['Cache-Control'] = 'private, no-store'
    return response
