import time
import uuid
import jwt
from django.conf import settings
from rest_framework.authentication import BaseAuthentication, get_authorization_header
from rest_framework.exceptions import AuthenticationFailed
from apps.accounts.models import User

def generate_access_token(user: User) -> str:
    now = int(time.time())
    payload = {
        'sub': str(user.id),
        'email': user.email,
        'token_type': 'access',
        'iat': now,
        'exp': now + settings.JWT_ACCESS_TTL,
        'jti': str(uuid.uuid4())
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm='HS256')

def generate_refresh_token(user: User) -> str:
    now = int(time.time())
    payload = {
        'sub': str(user.id),
        'email': user.email,
        'token_type': 'refresh',
        'iat': now,
        'exp': now + settings.JWT_REFRESH_TTL,
        'jti': str(uuid.uuid4())
    }
    return jwt.encode(payload, settings.JWT_SECRET, algorithm='HS256')

def verify_token(token: str, expected_type: str = 'access') -> dict:
    try:
        payload = jwt.decode(token, settings.JWT_SECRET, algorithms=['HS256'])
        if payload.get('token_type') != expected_type:
            raise AuthenticationFailed(f'Invalid token type. Expected {expected_type}')
        return payload
    except jwt.ExpiredSignatureError:
        raise AuthenticationFailed('Token has expired')
    except jwt.InvalidTokenError as e:
        raise AuthenticationFailed(f'Invalid token: {str(e)}')

class JWTAuthentication(BaseAuthentication):
    def authenticate_header(self, request):
        return 'Bearer'

    def authenticate(self, request):
        auth_header = get_authorization_header(request).split()
        if not auth_header or auth_header[0].lower() != b'bearer':
            return None

        if len(auth_header) == 1:
            raise AuthenticationFailed('Invalid bearer token header: No credentials provided')
        elif len(auth_header) > 2:
            raise AuthenticationFailed('Invalid bearer token header: Token contains spaces')

        token = auth_header[1].decode('utf-8')
        payload = verify_token(token, expected_type='access')

        user_id = payload.get('sub')
        try:
            user = User.objects.get(id=user_id)
        except User.DoesNotExist:
            raise AuthenticationFailed('User not found')

        if not user.is_active:
            raise AuthenticationFailed('User account is disabled')

        return (user, token)
