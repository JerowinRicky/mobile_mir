from rest_framework import serializers
from apps.devices.models import Device

class DeviceRegisterSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = (
            'hardware_fingerprint',
            'device_name',
            'device_model',
            'android_version',
            'app_version',
            'public_key_pem'
        )

    def validate_public_key_pem(self, value):
        if 'BEGIN PUBLIC KEY' not in value or 'END PUBLIC KEY' not in value:
            raise serializers.ValidationError("Invalid PEM format for public key.")
        return value

class DeviceSerializer(serializers.ModelSerializer):
    class Meta:
        model = Device
        fields = (
            'id',
            'hardware_fingerprint',
            'device_name',
            'device_model',
            'android_version',
            'app_version',
            'status',
            'last_seen_at',
            'registered_at'
        )
        read_only_fields = fields
