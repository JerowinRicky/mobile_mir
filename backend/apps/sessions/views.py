import hashlib
import secrets
from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from apps.devices.models import Device, DeviceStatus
from apps.sessions.models import RemoteSession, SessionStatus
from apps.sessions.serializers import CreateSessionSerializer, RemoteSessionSerializer
from apps.sessions.services import generate_turn_credentials
from apps.audit.utils import log_audit_event, get_client_ip

class CreateSessionView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request):
        serializer = CreateSessionSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        device_id = serializer.validated_data['device_id']
        try:
            device = Device.objects.get(id=device_id, user=request.user, status=DeviceStatus.PAIRED)
        except Device.DoesNotExist:
            return Response(
                {'detail': 'Device not found or not paired with your account.'},
                status=status.HTTP_404_NOT_FOUND
            )

        session_token = secrets.token_urlsafe(32)
        client_ip = get_client_ip(request)
        client_ua = request.META.get('HTTP_USER_AGENT', '')

        session = RemoteSession.objects.create(
            user=request.user,
            device=device,
            session_token=session_token,
            status=SessionStatus.INITIALIZING,
            client_ip=client_ip,
            client_user_agent=client_ua
        )

        turn_config = generate_turn_credentials(str(request.user.id))

        log_audit_event(
            event_type='SESSION_CREATE',
            user=request.user,
            device_id=device.id,
            request=request,
            metadata={'session_id': str(session.id)}
        )

        return Response({
            'session': RemoteSessionSerializer(session).data,
            'turn_config': turn_config
        }, status=status.HTTP_201_CREATED)

class TerminateSessionView(APIView):
    permission_classes = [IsAuthenticated]

    def post(self, request, session_id):
        try:
            session = RemoteSession.objects.get(id=session_id, user=request.user)
        except RemoteSession.DoesNotExist:
            return Response({'detail': 'Session not found'}, status=status.HTTP_404_NOT_FOUND)

        session.terminate(reason=request.data.get('reason', 'User Requested Disconnect'))

        log_audit_event(
            event_type='SESSION_TERMINATE',
            user=request.user,
            device_id=session.device_id,
            request=request,
            metadata={'session_id': str(session.id), 'duration': session.duration_seconds}
        )

        return Response({'message': 'Session terminated', 'duration_seconds': session.duration_seconds}, status=status.HTTP_200_OK)

class TurnCredentialsView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        credentials = generate_turn_credentials(str(request.user.id))
        return Response(credentials, status=status.HTTP_200_OK)

class SessionHistoryView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        sessions = RemoteSession.objects.filter(user=request.user)
        serializer = RemoteSessionSerializer(sessions, many=True)
        return Response(serializer.data, status=status.HTTP_200_OK)

class ActiveDeviceSessionView(APIView):
    permission_classes = [AllowAny]

    def get(self, request):
        device_token = request.query_params.get('device_token')
        if not device_token:
            return Response({'detail': 'device_token required'}, status=status.HTTP_401_UNAUTHORIZED)

        token_hash = hashlib.sha256(device_token.encode('utf-8')).hexdigest()
        try:
            device = Device.objects.get(device_token_hash=token_hash)
        except Device.DoesNotExist:
            return Response({'detail': 'Invalid device token'}, status=status.HTTP_401_UNAUTHORIZED)

        # Find any INITIALIZING, CONNECTING, or ACTIVE session for this device
        session = RemoteSession.objects.filter(
            device=device,
            status__in=[SessionStatus.INITIALIZING, SessionStatus.CONNECTING, SessionStatus.ACTIVE]
        ).select_related('user').order_by('-started_at').first()

        if not session:
            return Response({'has_active_session': False}, status=status.HTTP_200_OK)

        turn_config = generate_turn_credentials(str(session.user_id) if session.user_id else str(device.id))

        return Response({
            'has_active_session': True,
            'session_id': str(session.id),
            'status': session.status,
            'user_email': session.user.email if session.user else 'Authorized Web User',
            'turn_config': turn_config
        }, status=status.HTTP_200_OK)
