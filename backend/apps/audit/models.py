import uuid
from django.db import models
from django.utils import timezone
from django.conf import settings

class AuditLog(models.Model):
    id = models.UUIDField(primary_key=True, default=uuid.uuid4, editable=False)
    user = models.ForeignKey(settings.AUTH_USER_MODEL, on_delete=models.SET_NULL, null=True, blank=True, related_name='audit_logs')
    device_id = models.UUIDField(null=True, blank=True, db_index=True)
    event_type = models.CharField(max_length=100, db_index=True)
    severity = models.CharField(max_length=20, default='INFO')
    ip_address = models.GenericIPAddressField(null=True, blank=True)
    user_agent = models.TextField(blank=True, default='')
    metadata = models.JSONField(default=dict, blank=True)
    created_at = models.DateTimeField(default=timezone.now, db_index=True)

    class Meta:
        db_table = 'audit_logs'
        ordering = ['-created_at']

    def __str__(self):
        return f"{self.created_at} [{self.severity}] {self.event_type} - User: {self.user_id}"
