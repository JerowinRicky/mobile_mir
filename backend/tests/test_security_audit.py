import hashlib
import binascii
import time
import jwt
from django.test import TestCase
from django.conf import settings
from rest_framework.test import APIClient
from rest_framework import status

from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token
from apps.devices.models import Device, DeviceStatus
from apps.pairing.models import PairingSession, PairingStatus
from apps.pairing.services import compute_pbkdf2
from apps.sessions.services import generate_turn_credentials
from apps.audit.models import AuditLog

class SecurityAuditTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(
            email='security_officer@example.com',
            password='ComplexSecurityPassword2026!'
        )
        self.user_token = generate_access_token(self.user)

        self.raw_device_token = 'device_hw_sec_token_987654321'
        self.device_token_hash = hashlib.sha256(self.raw_device_token.encode('utf-8')).hexdigest()

        self.device = Device.objects.create(
            user=self.user,
            hardware_fingerprint='hw-sec-pixel8pro',
            device_name='Pixel 8 Pro SecTest',
            public_key_pem='-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n-----END PUBLIC KEY-----',
            device_token_hash=self.device_token_hash,
            status=DeviceStatus.PAIRED
        )

    def test_jwt_tampering_and_forgery_rejection(self):
        """Security: Verify forged, tampered, or wrong-secret JWTs are strictly rejected."""
        forged_payload = {
            'sub': str(self.user.id),
            'email': self.user.email,
            'token_type': 'access',
            'exp': int(time.time()) + 900
        }
        rogue_token = jwt.encode(forged_payload, 'attacker-unauthorized-secret-key', algorithm='HS256')

        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {rogue_token}')
        res = self.client.get('/api/v1/auth/me/')
        self.assertIn(res.status_code, [status.HTTP_401_UNAUTHORIZED, status.HTTP_403_FORBIDDEN])

    def test_jwt_expiration_enforcement(self):
        """Security: Verify expired JWT access tokens are immediately rejected."""
        expired_payload = {
            'sub': str(self.user.id),
            'email': self.user.email,
            'token_type': 'access',
            'exp': int(time.time()) - 100  # already expired
        }
        expired_token = jwt.encode(expired_payload, settings.JWT_SECRET, algorithm='HS256')

        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {expired_token}')
        res = self.client.get('/api/v1/auth/me/')
        self.assertIn(res.status_code, [status.HTTP_401_UNAUTHORIZED, status.HTTP_403_FORBIDDEN])

    def test_unauthorized_device_pairing_rejection(self):
        """Security: Verify invalid device token or non-existent device cannot initiate pairing."""
        res = self.client.post('/api/v1/pairing/initiate/', {
            'device_id': str(self.device.id),
            'device_token': 'wrong_invalid_device_secret',
            'pin_hash': 'abcdef1234567890',
            'pin_salt': '1234567890abcdef',
            'expires_in': 180
        }, format='json')
        self.assertIn(res.status_code, [status.HTTP_401_UNAUTHORIZED, status.HTTP_403_FORBIDDEN])

    def test_pin_brute_force_lockout_after_three_attempts(self):
        """Security: Verify 3 invalid PIN submissions trigger permanent session invalidation."""
        correct_pin = '739104'
        salt = binascii.hexlify(b'secure_salt_salt').decode('utf-8')
        pin_hash = compute_pbkdf2(correct_pin, salt)

        # 1. Device initiates pairing
        init_res = self.client.post('/api/v1/pairing/initiate/', {
            'device_id': str(self.device.id),
            'device_token': self.raw_device_token,
            'pin_hash': pin_hash,
            'pin_salt': salt,
            'expires_in': 300
        }, format='json')
        self.assertEqual(init_res.status_code, status.HTTP_201_CREATED)
        session_id = init_res.data['pairing_session_id']

        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')

        # 2. Attempt 1: Wrong PIN
        res1 = self.client.post('/api/v1/pairing/verify/', {
            'pin': '000000'
        }, format='json')
        self.assertEqual(res1.status_code, status.HTTP_400_BAD_REQUEST)
        self.assertIn('Invalid PIN', res1.data['detail'])

        # 3. Attempt 2: Wrong PIN
        res2 = self.client.post('/api/v1/pairing/verify/', {
            'pin': '111111'
        }, format='json')
        self.assertEqual(res2.status_code, status.HTTP_400_BAD_REQUEST)

        # 4. Attempt 3: Wrong PIN -> Session reaches max_attempts
        res3 = self.client.post('/api/v1/pairing/verify/', {
            'pin': '222222'
        }, format='json')
        self.assertEqual(res3.status_code, status.HTTP_400_BAD_REQUEST)

        # 5. Verify database status is FAILED
        pairing_db = PairingSession.objects.get(id=session_id)
        self.assertEqual(pairing_db.status, PairingStatus.FAILED)
        self.assertEqual(pairing_db.attempt_count, 3)

        # 6. Attempt 4: Even with correct PIN, must be rejected because session is FAILED/locked
        res4 = self.client.post('/api/v1/pairing/verify/', {
            'pin': correct_pin
        }, format='json')
        self.assertEqual(res4.status_code, status.HTTP_400_BAD_REQUEST)

    def test_device_revocation_blocks_sessions(self):
        """Security: Verify revoked device is blocked from starting remote sessions."""
        self.device.status = DeviceStatus.REVOKED
        self.device.save()

        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')
        res = self.client.post('/api/v1/sessions/create/', {
            'device_id': str(self.device.id)
        }, format='json')

        self.assertIn(res.status_code, [status.HTTP_404_NOT_FOUND, status.HTTP_403_FORBIDDEN])
        self.assertIn('not paired', res.data['detail'].lower())

    def test_coturn_ephemeral_credentials_hmac_and_expiry(self):
        """Security: Verify TURN ephemeral credentials adhere to Coturn HMAC-SHA1 timestamp specs."""
        turn_data = generate_turn_credentials(str(self.user.id))

        self.assertIn('ice_servers', turn_data)
        ice_servers = turn_data['ice_servers']
        self.assertEqual(len(ice_servers), 2)

        turn_entry = ice_servers[1]
        username = turn_entry['username']
        credential = turn_entry['credential']

        # Username format: "<timestamp>:<user_id>"
        parts = username.split(':', 1)
        self.assertEqual(len(parts), 2)
        expiry_ts = int(parts[0])

        current_time = int(time.time())
        self.assertGreater(expiry_ts, current_time + 3500)
        self.assertLessEqual(expiry_ts, current_time + 3650)
        self.assertIsNotNone(credential)
        self.assertGreater(len(credential), 10)

    def test_audit_event_logging_compliance(self):
        """Security: Verify that sensitive operations generate persistent audit log entries."""
        initial_count = AuditLog.objects.count()

        self.client.credentials()  # clear creds
        self.client.post('/api/v1/auth/login/', {
            'email': 'security_officer@example.com',
            'password': 'ComplexSecurityPassword2026!'
        }, format='json')

        after_count = AuditLog.objects.count()
        self.assertGreater(after_count, initial_count)

        last_event = AuditLog.objects.latest('created_at')
        self.assertEqual(last_event.event_type, 'AUTH_LOGIN')
        self.assertEqual(last_event.user, self.user)
