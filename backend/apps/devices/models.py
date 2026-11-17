import uuid
from django.db import models
from django.conf import settings
from django.utils import timezone

class DeviceStatus(models.TextChoices):
    UNPAIRED = 'UNPAIRED', 'Unpaired'
    PENDING_APPROVAL = 'PENDING_APPROVAL', 'Pending Approval'
    PAIRED = 'PAIRED', 'Paired'
    REVOKED = 'REVOKED', 'Revoked'

class Device(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.SET_NULL,
        null=True,
        blank=True,
        related_name='devices'
    )
    hardware_fingerprint = models.CharField(max_length=255, unique=True, db_index=True)
    device_name = models.CharField(max_length=100)
    device_model = models.CharField(max_length=100, blank=True, default='')
    android_version = models.CharField(max_length=20, blank=True, default='')
    app_version = models.CharField(max_length=20, blank=True, default='')
    public_key_pem = models.TextField()
    device_token_hash = models.CharField(max_length=255)
    status = models.CharField(
        max_length=30,
        choices=DeviceStatus.choices,
        default=DeviceStatus.UNPAIRED,
        db_index=True
    )
    last_seen_ip = models.GenericIPAddressField(null=True, blank=True)
    last_seen_at = models.DateTimeField(null=True, blank=True)
    registered_at = models.DateTimeField(default=timezone.now)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'devices'
        ordering = ['-registered_at']

    def __str__(self):
        return f"{self.device_name} ({self.hardware_fingerprint[:8]}...) - {self.status}"
