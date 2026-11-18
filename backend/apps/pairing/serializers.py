from rest_framework import serializers

class InitiatePairingSerializer(serializers.Serializer):
    device_id = serializers.CharField(max_length=255)
    device_token = serializers.CharField()
    pin_hash = serializers.CharField(max_length=255)
    pin_salt = serializers.CharField(max_length=64)
    expires_in = serializers.IntegerField(default=300, min_value=60, max_value=600)

class VerifyPinSerializer(serializers.Serializer):
    pin = serializers.CharField(max_length=10)

class ApprovePairingSerializer(serializers.Serializer):
    pairing_session_id = serializers.UUIDField()
    device_token = serializers.CharField()
    approved = serializers.BooleanField()
    signed_challenge = serializers.CharField(required=False, allow_blank=True, default='')
