import hashlib
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.throttling import AnonRateThrottle, UserRateThrottle
from apps.devices.models import Device
from apps.pairing.models import PairingSession, PairingStatus
from apps.pairing.serializers import InitiatePairingSerializer, VerifyPinSerializer, ApprovePairingSerializer
from apps.pairing.services import PairingService
from apps.audit.utils import log_audit_event, get_client_ip

class PairingRateThrottle(UserRateThrottle):
    rate = '10/minute'

class InitiatePairingView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = InitiatePairingSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        device_id = serializer.validated_data['device_id']
        device_token = serializer.validated_data['device_token']
        token_hash = hashlib.sha256(device_token.encode('utf-8')).hexdigest()

        try:
            device = Device.objects.get(id=device_id, device_token_hash=token_hash)
        except Device.DoesNotExist:
            return Response({'detail': 'Invalid device credentials'}, status=status.HTTP_401_UNAUTHORIZED)

        session = PairingService.initiate_pairing(
            device=device,
            pin_hash=serializer.validated_data['pin_hash'],
            pin_salt=serializer.validated_data['pin_salt'],
            expires_in_seconds=serializer.validated_data['expires_in']
        )

        log_audit_event(
            event_type='PAIR_INITIATE',
            device_id=device.id,
            request=request,
            metadata={'session_id': str(session.id)}
        )

        return Response({
            'pairing_session_id': str(session.id),
            'status': session.status,
            'expires_at': session.expires_at.isoformat()
        }, status=status.HTTP_201_CREATED)

class VerifyPinView(APIView):
    permission_classes = [IsAuthenticated]
    throttle_classes = [PairingRateThrottle]

    def post(self, request):
        serializer = VerifyPinSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        raw_pin = serializer.validated_data['pin']
        client_ip = get_client_ip(request)
        client_ua = request.META.get('HTTP_USER_AGENT', '')

        success, message, session = PairingService.verify_pin(
            user=request.user,
            raw_pin=raw_pin,
            client_ip=client_ip,
            client_ua=client_ua
        )

        if not success:
            log_audit_event(
                event_type='PAIR_VERIFY_FAILED',
                user=request.user,
                request=request,
                severity='WARN',
                metadata={'reason': message}
            )
            return Response({'detail': message}, status=status.HTTP_400_BAD_REQUEST)

        log_audit_event(
            event_type='PAIR_VERIFY_SUCCESS',
            user=request.user,
            device_id=session.device_id,
            request=request,
            metadata={'session_id': str(session.id), 'device_name': session.device.device_name}
        )

        return Response({
            'pairing_session_id': str(session.id),
            'device_id': str(session.device.id),
            'device_name': session.device.device_name,
            'status': session.status,
            'message': message
        }, status=status.HTTP_200_OK)

class ApprovePairingView(APIView):
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = ApprovePairingSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        session_id = serializer.validated_data['pairing_session_id']
        device_token = serializer.validated_data['device_token']
        approved = serializer.validated_data['approved']
        signed_challenge = serializer.validated_data.get('signed_challenge')

        token_hash = hashlib.sha256(device_token.encode('utf-8')).hexdigest()

        try:
            device = Device.objects.get(device_token_hash=token_hash)
        except Device.DoesNotExist:
            return Response({'detail': 'Invalid device credentials'}, status=status.HTTP_401_UNAUTHORIZED)

        success, message = PairingService.approve_pairing(
            session_id=str(session_id),
            device=device,
            approved=approved,
            signed_challenge=signed_challenge
        )

        if not success:
            return Response({'detail': message}, status=status.HTTP_400_BAD_REQUEST)

        log_audit_event(
            event_type='PAIR_APPROVED' if approved else 'PAIR_REJECTED',
            device_id=device.id,
            request=request,
            metadata={'session_id': str(session_id)}
        )

        return Response({
            'status': 'PAIRED' if approved else 'REJECTED',
            'message': message
        }, status=status.HTTP_200_OK)

class CheckPairingStatusView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request, session_id):
        try:
            session = PairingSession.objects.get(id=session_id, target_user=request.user)
        except PairingSession.DoesNotExist:
            return Response({'detail': 'Pairing session not found'}, status=status.HTTP_404_NOT_FOUND)

        return Response({
            'pairing_session_id': str(session.id),
            'status': session.status,
            'device_id': str(session.device.id) if session.device else None,
            'device_name': session.device.device_name if session.device else None
        }, status=status.HTTP_200_OK)
