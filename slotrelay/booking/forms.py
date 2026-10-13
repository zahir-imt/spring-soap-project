from datetime import timedelta
from django import forms
from django.contrib.auth.forms import UserCreationForm
from django.contrib.auth.models import User
from django.db import transaction
from django.utils import timezone
from .models import Business, Slot

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
        fields = ['service','starts','duration','price']
        widgets = {'starts':forms.DateTimeInput(attrs={'type':'datetime-local'})}
        labels = {'starts':'Start time (Toronto time)','duration':'Duration in minutes','price':'Price (CAD)'}
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
def save_slot(form, business):
    Business.objects.select_for_update().get(pk=business.pk)
    start = form.cleaned_data['starts']
    end = start + timedelta(minutes=form.cleaned_data['duration'])
    for existing in Slot.objects.filter(business=business,cancelled=False,starts__lt=end):
        if existing.starts + timedelta(minutes=existing.duration) > start:
            raise forms.ValidationError('This time overlaps another appointment. Each business supports one appointment at a time.')
    slot = form.save(commit=False)
    slot.business = business
    slot.save()
    return slot
