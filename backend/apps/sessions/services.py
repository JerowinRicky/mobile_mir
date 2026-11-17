import time
import hmac
import hashlib
import base64
from django.conf import settings

def generate_turn_credentials(user_identifier: str) -> dict:
    ttl = getattr(settings, 'COTURN_CREDENTIAL_TTL', 3600)
    expiry = int(time.time()) + ttl
    username = f"{expiry}:{user_identifier}"

    secret = getattr(settings, 'COTURN_AUTH_SECRET', 'antigravity-coturn-static-secret-2026').encode('utf-8')
    digest = hmac.new(secret, username.encode('utf-8'), hashlib.sha1).digest()
    password = base64.b64encode(digest).decode('utf-8')

    return {
        'ice_servers': [
            {'urls': [getattr(settings, 'COTURN_STUN_SERVER', 'stun:stun.l.google.com:19302')]},
            {
                'urls': [getattr(settings, 'COTURN_TURN_SERVER', 'turn:turn.remotedevice.internal:3478')],
                'username': username,
                'credential': password
            }
        ],
        'ttl_seconds': ttl
    }
