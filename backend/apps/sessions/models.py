import uuid
from django.db import models
from django.conf import settings
from django.utils import timezone
from apps.devices.models import Device

class SessionStatus(models.TextChoices):
    INITIALIZING = 'INITIALIZING', 'Initializing'
    CONNECTING = 'CONNECTING', 'Connecting'
    ACTIVE = 'ACTIVE', 'Active'
    TERMINATED = 'TERMINATED', 'Terminated'
    FAILED = 'FAILED', 'Failed'

class RemoteSession(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.CASCADE, related_name='remote_sessions')
    device = models.ForeignKey(Device, on_delete=models.CASCADE, related_name='remote_sessions')
    session_token = models.CharField(max_length=255, unique=True, db_index=True)
    status = models.CharField(
        max_length=30,
        choices=SessionStatus.choices,
        default=SessionStatus.INITIALIZING,
        db_index=True
    )
    client_ip = models.GenericIPAddressField(null=True, blank=True)
    client_user_agent = models.TextField(blank=True, default='')
    turn_used = models.BooleanField(default=False)
    started_at = models.DateTimeField(default=timezone.now)
    ended_at = models.DateTimeField(null=True, blank=True)
    duration_seconds = models.IntegerField(null=True, blank=True)
    termination_reason = models.CharField(max_length=100, blank=True, default='')

    class Meta:
        db_table = 'remote_sessions'
        ordering = ['-started_at']

    def terminate(self, reason='User Terminated'):
        if self.status != SessionStatus.TERMINATED:
            self.status = SessionStatus.TERMINATED
            self.ended_at = timezone.now()
            self.duration_seconds = int((self.ended_at - self.started_at).total_seconds())
            self.termination_reason = reason
            self.save()

    def __str__(self):
        return f"Session {self.id} (User: {self.user.email} -> {self.device.device_name}) [{self.status}]"
