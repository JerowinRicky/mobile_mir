import uuid
from django.db import models
from django.conf import settings
from django.utils import timezone
from apps.devices.models import Device

class PairingStatus(models.TextChoices):
    PENDING_VERIFICATION = 'PENDING_VERIFICATION', 'Pending Verification'
    AWAITING_APPROVAL = 'AWAITING_APPROVAL', 'Awaiting Device Approval'
    APPROVED = 'APPROVED', 'Approved'
    EXPIRED = 'EXPIRED', 'Expired'
    FAILED = 'FAILED', 'Failed'

class PairingSession(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    device = models.ForeignKey(Device, on_delete=models.CASCADE, related_name='pairing_sessions')
    target_user = models.ForeignKey(
        settings.AUTH_USER_MODEL,
        on_delete=models.CASCADE,
        null=True,
        blank=True,
        related_name='pairing_requests'
    )
    pin_salt = models.CharField(max_length=64)
    pin_hash = models.CharField(max_length=255)
    attempt_count = models.IntegerField(default=0)
    max_attempts = models.IntegerField(default=3)
    status = models.CharField(
        max_length=30,
        choices=PairingStatus.choices,
        default=PairingStatus.PENDING_VERIFICATION,
        db_index=True
    )
    client_ip = models.GenericIPAddressField(null=True, blank=True)
    client_user_agent = models.TextField(blank=True, default='')
    expires_at = models.DateTimeField(db_index=True)
    created_at = models.DateTimeField(default=timezone.now)
    updated_at = models.DateTimeField(auto_now=True)

    class Meta:
        db_table = 'pairing_sessions'
        ordering = ['-created_at']

    @property
    def is_expired(self):
        return timezone.now() >= self.expires_at

    def __str__(self):
        return f"Pairing {self.id} for {self.device.device_name} ({self.status})"
