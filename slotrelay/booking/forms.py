from datetime import timedelta, timezone as dt_timezone
from django import forms
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.models import User
from django.db import transaction
from django.utils import timezone
from .models import Business, Slot, Staff

class SignupForm(UserCreationForm):
    class Meta:
        model = User
        fields = ['username','password1','password2']

class BusinessForm(forms.ModelForm):
    class Meta:
        model = Business
        fields = ['name','category','city','address','description']
        widgets = {'description':forms.Textarea(attrs={'rows':3})}

class SlotForm(forms.ModelForm):
    class Meta:
        model = Slot
        fields = ['service','staff','starts','duration','price']
        widgets = {'starts':forms.DateTimeInput(attrs={'type':'datetime-local'})}
        labels = {'starts':'Start time (Toronto time)','duration':'Duration in minutes','price':'Price (CAD)'}
    def __init__(self, *args, business=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['staff'].queryset = Staff.objects.filter(business=business, active=True) if business else Staff.objects.none()
        self.fields['staff'].empty_label = 'Unassigned (reserves the whole business)'
    def clean_starts(self):
        value = self.cleaned_data['starts']
        if value <= timezone.now(): raise forms.ValidationError('Choose a future date and time.')
        return value
    def clean_duration(self):
        value = self.cleaned_data['duration']
        if not 5 <= value <= 480: raise forms.ValidationError('Choose between 5 and 480 minutes.')
        return value
    def clean_price(self):
        value = self.cleaned_data['price']
        if value < 0: raise forms.ValidationError('Price cannot be negative.')
        return value

@transaction.atomic
def create_slot(business, **values):
    Business.objects.select_for_update().get(pk=business.pk)
    staff = values.get('staff')
    if staff and not Staff.objects.filter(pk=staff.pk, business=business, active=True).exists():
        raise forms.ValidationError('Choose an active staff member from your business.')
    start = values['starts'].astimezone(dt_timezone.utc)
    values['starts'] = start
    if start <= timezone.now(): raise forms.ValidationError('Choose a future start time.')
    end = start + timedelta(minutes=values['duration'])
    existing = Slot.objects.filter(business=business,cancelled=False,starts__lt=end)
    if staff:
        from django.db.models import Q
        existing = existing.filter(Q(staff=staff) | Q(staff__isnull=True))
    for slot in existing:
        if slot.starts + timedelta(minutes=slot.duration) > start:
            raise forms.ValidationError('This time overlaps an appointment for this staff member or an unassigned business appointment.')
    slot = Slot(business=business, **values)
    slot.full_clean()
    slot.save()
    return slot

@transaction.atomic
def save_slot(form, business):
    return create_slot(business, **{key: form.cleaned_data[key] for key in ['service','starts','duration','price','staff']})

class BatchSlotForm(SlotForm):
    count = forms.IntegerField(min_value=2, max_value=50, initial=5, label='Number of consecutive appointments')
    gap = forms.IntegerField(min_value=0, max_value=120, initial=0, label='Break between appointments (minutes)')

@transaction.atomic
def save_batch(form, business):
    values = {key: form.cleaned_data[key] for key in ['service','starts','duration','price','staff']}
    result = []
    for index in range(form.cleaned_data['count']):
        result.append(create_slot(business, **dict(values, starts=values['starts'].astimezone(dt_timezone.utc)+timedelta(minutes=index*(values['duration']+form.cleaned_data['gap'])))))
    return result

class StaffForm(forms.ModelForm):
    class Meta:
        model = Staff
        fields = ['name']

class FlexibleWaitlistForm(forms.Form):
    business = forms.ModelChoiceField(queryset=Business.objects.none())
    service = forms.CharField(max_length=100, help_text='Enter the exact service name shown in the listings.')
    earliest = forms.DateTimeField(widget=forms.DateTimeInput(attrs={'type':'datetime-local'}), label='Earliest start (Toronto time)')
    latest = forms.DateTimeField(widget=forms.DateTimeInput(attrs={'type':'datetime-local'}), label='Latest start (Toronto time)')
    max_price = forms.DecimalField(min_value=0, max_digits=8, decimal_places=2, label='Maximum price (CAD)')
    def __init__(self, *args, user=None, **kwargs):
        super().__init__(*args, **kwargs)
        self.fields['business'].queryset = Business.objects.exclude(owner=user).order_by('name')
    def clean(self):
        data = super().clean()
        start, end = data.get('earliest'), data.get('latest')
        if start and end and (start <= timezone.now() or end < start or end-start > timedelta(days=31)):
            raise forms.ValidationError('Choose a future window of up to 31 days, with the latest time after the earliest.')
        return data


class ExploreForm(forms.Form):
    q = forms.CharField(required=False,max_length=100,label='Service or business')
    city = forms.CharField(required=False,max_length=80)
    category = forms.ChoiceField(required=False,choices=[('', 'All categories')]+Business._meta.get_field('category').choices)
    from_date = forms.DateField(required=False,widget=forms.DateInput(attrs={'type':'date'}),label='From date (Toronto)')
    to_date = forms.DateField(required=False,widget=forms.DateInput(attrs={'type':'date'}),label='Through date (Toronto)')
    max_price = forms.DecimalField(required=False,min_value=0,max_digits=8,decimal_places=2,label='Maximum price (CAD)')
    availability = forms.ChoiceField(required=False,choices=[('', 'All appointments'),('open','Available to book'),('waitlist','Waitlist available')])
    sort = forms.ChoiceField(required=False,choices=[('', 'Soonest first'),('price','Lowest price'),('-price','Highest price')],label='Sort by')
    def clean(self):
        data=super().clean()
        if data.get('from_date') and data.get('to_date') and data['from_date'] > data['to_date']:
            raise forms.ValidationError('The end date must be on or after the start date.')
        return data
