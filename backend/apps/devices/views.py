import hashlib
import secrets
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from apps.devices.models import Device, DeviceStatus
from apps.devices.serializers import DeviceRegisterSerializer, DeviceSerializer
from apps.audit.utils import log_audit_event

class DeviceRegisterView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = DeviceRegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        fingerprint = serializer.validated_data['hardware_fingerprint']
        raw_device_token = secrets.token_urlsafe(32)
        token_hash = hashlib.sha256(raw_device_token.encode('utf-8')).hexdigest()

        device, created = Device.objects.update_or_create(
            hardware_fingerprint=fingerprint,
            defaults={
                'device_name': serializer.validated_data['device_name'],
                'device_model': serializer.validated_data.get('device_model', ''),
                'android_version': serializer.validated_data.get('android_version', ''),
                'app_version': serializer.validated_data.get('app_version', ''),
                'public_key_pem': serializer.validated_data['public_key_pem'],
                'device_token_hash': token_hash,
                'status': DeviceStatus.UNPAIRED
            }
        )

        log_audit_event(
            event_type='DEVICE_REGISTER',
            device_id=device.id,
            request=request,
            metadata={'device_name': device.device_name, 'created': created}
        )

        return Response({
            'device_id': str(device.id),
            'device_token': raw_device_token,
            'status': device.status
        }, status=status.HTTP_201_CREATED if created else status.HTTP_200_OK)

class DeviceListView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        devices = Device.objects.filter(user=request.user)
        serializer = DeviceSerializer(devices, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

class DeviceRevokeView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, device_id):
        try:
            device = Device.objects.get(id=device_id, user=request.user)
        except Device.DoesNotExist:
            return Response({'detail': 'Device not found'}, status=status.HTTP_404_NOT_FOUND)

        device.status = DeviceStatus.REVOKED
        device.user = None
        device.save()

        log_audit_event(
            event_type='DEVICE_REVOKE',
            user=request.user,
            device_id=device.id,
            request=request,
            severity='WARN',
            metadata={'device_name': device.device_name}
        )

        return Response({'message': 'Device successfully revoked and unlinked.'}, status=status.HTTP_200_OK)
