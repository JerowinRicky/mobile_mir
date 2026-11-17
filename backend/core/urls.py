from django.contrib import admin
from django.urls import path, include

urlpatterns = [
    path('admin/', admin.site.urls),
    path('api/v1/auth/', include('apps.accounts.urls')),
    path('api/v1/devices/', include('apps.devices.urls')),
    path('api/v1/pairing/', include('apps.pairing.urls')),
    path('api/v1/sessions/', include('apps.sessions.urls')),
    path('api/v1/audit/', include('apps.audit.urls')),
]
