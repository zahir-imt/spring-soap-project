from .models import Notice
def notification_count(request):
    return {'unread_count': Notice.objects.filter(user=request.user,read_at__isnull=True).count() if request.user.is_authenticated else 0}
