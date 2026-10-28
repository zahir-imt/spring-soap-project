import hashlib
from datetime import timedelta
from django import forms
from django.contrib import messages
from django.contrib.auth import login
from django.contrib.auth.decorators import login_required
from django.contrib.auth.forms import AuthenticationForm
from django.db import transaction
from django.db.models import Exists, OuterRef, Sum, Q
from django.core.paginator import Paginator
from django.http import Http404
from django.shortcuts import render, redirect, get_object_or_404
from django.utils import timezone
from django.views.decorators.http import require_POST
from .models import Business, Slot, Booking, WaitEntry, Notice, LoginAttempt
from .forms import SignupForm, BusinessForm, SlotForm, save_slot, ExploreForm
from .services import refresh_offers, book, join_waitlist, cancel_booking, leave_waitlist, cancel_slot, RuleError

def explore(request):
    refresh_offers()
    slots = Slot.objects.filter(cancelled=False,starts__gt=timezone.now()).select_related('business','staff').annotate(taken=Exists(Booking.objects.filter(slot=OuterRef('pk'),active=True)), held=Exists(WaitEntry.objects.filter(slot=OuterRef('pk'),status='offered')))
    form = ExploreForm(request.GET)
    if form.is_valid():
        values=form.cleaned_data
        if values['q']: slots=slots.filter(Q(service__icontains=values['q']) | Q(business__name__icontains=values['q']))
        if values['city']: slots=slots.filter(business__city__icontains=values['city'])
        if values['category']: slots=slots.filter(business__category=values['category'])
        if values['from_date']: slots=slots.filter(starts__date__gte=values['from_date'])
        if values['to_date']: slots=slots.filter(starts__date__lte=values['to_date'])
        if values['max_price'] is not None: slots=slots.filter(price__lte=values['max_price'])
        if values['availability']=='open': slots=slots.filter(taken=False,held=False)
        elif values['availability']=='waitlist': slots=slots.filter(Q(taken=True) | Q(held=True))
        slots=slots.order_by(*({'price':['price','starts','pk'],'-price':['-price','starts','pk']}.get(values['sort'],['starts','pk'])))
    else:
        slots=slots.none()
    page=Paginator(slots,24).get_page(request.GET.get('page'))
    params=request.GET.copy()
    params.pop('page',None)
    return render(request,'explore.html',{'slots':page,'page':page,'search_form':form,'filter_query':params.urlencode()})

def signup(request):
    form = SignupForm(request.POST or None)
    if request.method == 'POST' and form.is_valid():
        user = form.save()
        login(request,user)
        return redirect('dashboard')
    return render(request,'form.html',{'form':form,'title':'Make room for something good.','eyebrow':'CREATE YOUR ACCOUNT','button':'Create account','note':'Start as a customer. You can add a business from your dashboard.'})

def signin(request):
    form = AuthenticationForm(request, data=request.POST or None)
    if request.method == 'POST':
        key = hashlib.sha256(request.POST.get('username','').strip().casefold().encode()).hexdigest()
        now = timezone.now()
        with transaction.atomic():
            attempt, _ = LoginAttempt.objects.get_or_create(key=key,defaults={'until':now+timedelta(minutes=15)})
            if attempt.until <= now:
                attempt.count=0
                attempt.until=now+timedelta(minutes=15)
            if attempt.count >= 10:
                form.add_error(None,'Too many attempts. Please try again in 15 minutes.')
            elif form.is_valid():
                attempt.delete()
                login(request,form.get_user())
                return redirect('dashboard')
            else:
                attempt.count += 1
            attempt.save()
    return render(request,'form.html',{'form':form,'title':'Welcome back.','eyebrow':'YOUR NEXT OPENING','button':'Sign in','note':'Use your SlotRelay account. StockBridge accounts are separate.'})

@login_required
def dashboard(request):
    refresh_offers()
    business = Business.objects.filter(owner=request.user).first()
    bookings = Booking.objects.filter(customer=request.user).select_related('slot__business','slot__staff').order_by('-slot__starts')[:50]
    waiting = WaitEntry.objects.filter(customer=request.user,status__in=['waiting','offered']).select_related('slot__business','slot__staff')
    return render(request,'dashboard.html',{'flexible_groups':request.user.flexiblewaitlist_set.filter(active=True).select_related('business'),'bookings':bookings,'waiting':waiting,'notices':Notice.objects.filter(user=request.user)[:12],'business':business,'now':timezone.now()})

@login_required
def business_edit(request):
    business = Business.objects.filter(owner=request.user).first()
    form = BusinessForm(request.POST or None,instance=business)
    if request.method == 'POST' and form.is_valid():
        obj=form.save(commit=False)
        obj.owner=request.user
        obj.save()
        messages.success(request,'Your business profile is ready.')
        return redirect('business')
    return render(request,'form.html',{'form':form,'title':'Your business, more discoverable.','eyebrow':'BUSINESS PROFILE','button':'Save business','note':'Your business name, address and description will be visible in listings.'})

@login_required
def business_dashboard(request):
    business = Business.objects.filter(owner=request.user).first()
    if not business: return redirect('business-edit')
    slots = Slot.objects.filter(business=business).select_related('staff').annotate(taken=Exists(Booking.objects.filter(slot=OuterRef('pk'),active=True))).order_by('-starts')[:100]
    bookings = Booking.objects.filter(slot__business=business,active=True).select_related('slot','customer').order_by('slot__starts')[:100]
    total = Booking.objects.filter(slot__business=business,active=True).aggregate(total=Sum('slot__price'))['total'] or 0
    return render(request,'business.html',{'business':business,'slots':slots,'bookings':bookings,'value':total,'now':timezone.now()})

@login_required
def slot_new(request):
    business=get_object_or_404(Business,owner=request.user)
    form=SlotForm(request.POST or None,business=business)
    if request.method == 'POST' and form.is_valid():
        try:
            save_slot(form,business)
        except forms.ValidationError as e:
            form.add_error(None,e)
        else:
            messages.success(request,'Your appointment is now listed.')
            return redirect('business')
    return render(request,'form.html',{'form':form,'title':'Turn an opening into an opportunity.','eyebrow':'PUBLISH AN APPOINTMENT','button':'Publish appointment','note':'All prices are CAD. Dates are shown in America/Toronto time. Appointments may overlap only for different staff. Unassigned appointments reserve the whole business.'})

@login_required
@require_POST
def action(request,kind,pk):
    actions={'book':book,'wait':join_waitlist,'cancel-booking':cancel_booking,'leave':leave_waitlist,'cancel-slot':cancel_slot}
    if kind not in actions: raise Http404
    try:
        actions[kind](pk,request.user)
        messages.success(request,'Done. Your dashboard is up to date.')
    except (Slot.DoesNotExist,Booking.DoesNotExist,WaitEntry.DoesNotExist): raise Http404
    except RuleError as e: messages.error(request,str(e))
    return redirect('business' if kind=='cancel-slot' else 'dashboard')

def csrf_failure(request, reason=''):
    # Never replay a rejected POST or echo submitted credentials.
    from django.views.decorators.csrf import requires_csrf_token
    from django.views.decorators.cache import never_cache
    @never_cache
    @requires_csrf_token
    def recovery(request):
        return render(request, 'csrf_failure.html', status=403)
    return recovery(request)
