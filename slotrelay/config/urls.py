from django.urls import path
from django.contrib.auth.views import LogoutView
from booking import views
urlpatterns = [path('',views.explore,name='explore'),path('signup/',views.signup,name='signup'),path('login/',views.signin,name='login'),path('logout/',LogoutView.as_view(),name='logout'),path('dashboard/',views.dashboard,name='dashboard'),path('business/',views.business_dashboard,name='business'),path('business/edit/',views.business_edit,name='business-edit'),path('business/new-slot/',views.slot_new,name='slot-new'),path('action/<str:kind>/<int:pk>/',views.action,name='action')]
