from rest_framework import serializers
from apps.sessions.models import RemoteSession

class CreateSessionSerializer(serializers.Serializer):
    device_id = serializers.UUIDField()

class RemoteSessionSerializer(serializers.ModelSerializer):
    device_name = serializers.CharField(source='device.device_name', read_only=True)

    class Meta:
        model = RemoteSession
        fields = (
            'id',
            'device',
            'device_name',
            'session_token',
            'status',
            'started_at',
            'ended_at',
            'duration_seconds',
            'termination_reason'
        )
        read_only_fields = fields
