from rest_framework import status
from rest_framework.views import APIView
from rest_framework.response import Response
from rest_framework.permissions import AllowAny, IsAuthenticated
from django.conf import settings
from apps.accounts.serializers import RegisterSerializer, LoginSerializer, RefreshTokenSerializer, UserSerializer
from apps.accounts.authentication import generate_access_token, generate_refresh_token, verify_token
from apps.accounts.models import User
from apps.audit.utils import log_audit_event

class RegisterView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RegisterSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.save()

        access_token = generate_access_token(user)
        refresh_token = generate_refresh_token(user)

        log_audit_event(
            event_type='USER_REGISTER',
            user=user,
            request=request,
            metadata={'email': user.email}
        )

        return Response({
            'user': UserSerializer(user).data,
            'access_token': access_token,
            'refresh_token': refresh_token,
            'expires_in': settings.JWT_ACCESS_TTL
        }, status=status.HTTP_201_CREATED)

class LoginView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        user = serializer.validated_data['user']

        access_token = generate_access_token(user)
        refresh_token = generate_refresh_token(user)

        log_audit_event(
            event_type='AUTH_LOGIN',
            user=user,
            request=request,
            metadata={'email': user.email}
        )

        return Response({
            'user': UserSerializer(user).data,
            'access_token': access_token,
            'refresh_token': refresh_token,
            'expires_in': settings.JWT_ACCESS_TTL
        }, status=status.HTTP_200_OK)

class RefreshTokenView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = RefreshTokenSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)
        token = serializer.validated_data['refresh_token']

        payload = verify_token(token, expected_type='refresh')
        user_id = payload.get('sub')

        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            return Response({'detail': 'User not found'}, status=status.HTTP_404_NOT_FOUND)

        if not user.is_active:
            return Response({'detail': 'User account is disabled'}, status=status.HTTP_403_FORBIDDEN)

        new_access_token = generate_access_token(user)
        new_refresh_token = generate_refresh_token(user)

        return Response({
            'access_token': new_access_token,
            'refresh_token': new_refresh_token,
            'expires_in': settings.JWT_ACCESS_TTL
        }, status=status.HTTP_200_OK)

class CurrentUserView(APIView):
    permission_classes = [IsAuthenticated]

    def get(self, request):
        serializer = UserSerializer(request.user)
        return Response(serializer.data, status=status.HTTP_200_OK)

class HealthCheckView(APIView):
    authentication_classes = []
    permission_classes = [AllowAny]

    def get(self, request):
        return Response({
            'status': 'healthy',
            'service': 'remotedevice-backend',
            'version': '1.0.0'
        }, status=status.HTTP_200_OK)

