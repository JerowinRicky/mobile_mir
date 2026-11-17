from django.urls import path
from apps.devices.views import DeviceRegisterView, DeviceListView, DeviceRevokeView

urlpatterns = [
    path('register/', DeviceRegisterView.as_view(), name='device-register'),
    path('', DeviceListView.as_view(), name='device-list'),
    path('<uuid:device_id>/revoke/', DeviceRevokeView.as_view(), name='device-revoke'),
]
