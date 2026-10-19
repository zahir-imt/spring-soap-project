from django.urls import path
from django.contrib.auth.views import LogoutView
from booking import views
urlpatterns = [path('',views.explore,name='explore'),path('signup/',views.signup,name='signup'),path('login/',views.signin,name='login'),path('logout/',LogoutView.as_view(),name='logout'),path('dashboard/',views.dashboard,name='dashboard'),path('business/',views.business_dashboard,name='business'),path('business/edit/',views.business_edit,name='business-edit'),path('business/new-slot/',views.slot_new,name='slot-new'),path('action/<str:kind>/<int:pk>/',views.action,name='action')]

from booking import workspace_views
urlpatterns += [
    path('bookings/<int:pk>/reschedule/', workspace_views.reschedule, name='reschedule'),
    path('business/calendar/', workspace_views.business_calendar, name='business-calendar'),
]

from booking import upgrade_views
urlpatterns += [
    path('business/staff/', upgrade_views.staff, name='staff'),
    path('business/staff/<int:pk>/toggle/', upgrade_views.staff_toggle, name='staff-toggle'),
    path('business/batch/', upgrade_views.batch, name='batch'),
    path('waitlist/flexible/', upgrade_views.flexible, name='flexible'),
    path('waitlist/flexible/<int:pk>/close/', upgrade_views.flexible_close, name='flexible-close'),
    path('notifications/', upgrade_views.inbox, name='inbox'),
    path('notifications/<int:pk>/read/', upgrade_views.notice_read, name='notice-read'),
    path('notifications/read-all/', upgrade_views.notices_read, name='notices-read'),
    path('bookings/<int:pk>/calendar.ics', upgrade_views.calendar_export, name='calendar-export'),
]
