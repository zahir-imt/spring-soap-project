from django.conf import settings
from django.db import models
from django.db.models import Q

class Business(models.Model):
    owner = models.OneToOneField(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    name = models.CharField(max_length=100)
    category = models.CharField(max_length=30, choices=[('Wellness','Wellness'),('Beauty','Beauty'),('Fitness','Fitness'),('Learning','Learning'),('Repairs','Repairs')])
    city = models.CharField(max_length=80)
    address = models.CharField(max_length=200)
    description = models.TextField(max_length=1000)
    def __str__(self): return self.name

class Slot(models.Model):
    business = models.ForeignKey(Business, on_delete=models.CASCADE)
    service = models.CharField(max_length=100)
    starts = models.DateTimeField()
    duration = models.PositiveIntegerField(default=30)
    price = models.DecimalField(max_digits=8, decimal_places=2)
    cancelled = models.BooleanField(default=False)
    class Meta:
        ordering = ['starts']
        constraints = [models.UniqueConstraint(fields=['business','starts'], name='unique_business_start'), models.CheckConstraint(condition=Q(price__gte=0), name='nonnegative_price'), models.CheckConstraint(condition=Q(duration__gte=5) & Q(duration__lte=480), name='valid_duration')]
    def __str__(self): return self.service

class Booking(models.Model):
    slot = models.ForeignKey(Slot, on_delete=models.CASCADE)
    customer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    created = models.DateTimeField(auto_now_add=True)
    active = models.BooleanField(default=True)
    rescheduled_to = models.ForeignKey('self', null=True, blank=True, on_delete=models.SET_NULL, related_name='rescheduled_from')
    class Meta:
        constraints = [models.UniqueConstraint(fields=['slot'], condition=Q(active=True), name='one_active_booking')]

class WaitEntry(models.Model):
    slot = models.ForeignKey(Slot, on_delete=models.CASCADE)
    customer = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    created = models.DateTimeField(auto_now_add=True)
    status = models.CharField(max_length=12, default='waiting', choices=[('waiting','Waiting'),('offered','Offered'),('accepted','Accepted'),('expired','Expired'),('left','Left')])
    expires = models.DateTimeField(null=True, blank=True)
    class Meta:
        ordering = ['created','pk']
        constraints = [models.UniqueConstraint(fields=['slot','customer'], condition=Q(status__in=['waiting','offered']), name='one_live_waitentry')]

class Notice(models.Model):
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE)
    text = models.CharField(max_length=300)
    created = models.DateTimeField(auto_now_add=True)
    class Meta: ordering = ['-created']

class LoginAttempt(models.Model):
    key = models.CharField(max_length=64, unique=True)
    count = models.PositiveIntegerField(default=0)
    until = models.DateTimeField()
