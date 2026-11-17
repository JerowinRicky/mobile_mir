import hashlib
import binascii
from django.test import TestCase
from rest_framework.test import APIClient
from rest_framework import status
from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token
from apps.devices.models import Device, DeviceStatus
from apps.pairing.models import PairingSession, PairingStatus
from apps.pairing.services import compute_pbkdf2

class PairingTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.initiate_url = '/api/v1/pairing/initiate/'
        self.verify_url = '/api/v1/pairing/verify/'
        self.approve_url = '/api/v1/pairing/approve/'

        self.user = User.objects.create_user(email='charlie@example.com', password='StrongPassword2026!')
        self.user_token = generate_access_token(self.user)

        self.raw_device_token = 'test_device_secret_token_123456789'
        self.device_token_hash = hashlib.sha256(self.raw_device_token.encode('utf-8')).hexdigest()

        self.device = Device.objects.create(
            hardware_fingerprint='device-hw-pairing-test',
            device_name='Galaxy S24',
            public_key_pem='-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n-----END PUBLIC KEY-----',
            device_token_hash=self.device_token_hash,
            status=DeviceStatus.UNPAIRED
        )

        # Setup test PIN
        self.test_pin = '849201'
        self.test_salt = binascii.hexlify(b'test_salt_16byte').decode('utf-8')
        self.test_pin_hash = compute_pbkdf2(self.test_pin, self.test_salt)

    def test_complete_pairing_lifecycle_success(self):
        # 1. Device initiates pairing
        init_res = self.client.post(self.initiate_url, {
            'device_id': str(self.device.id),
            'device_token': self.raw_device_token,
            'pin_hash': self.test_pin_hash,
            'pin_salt': self.test_salt,
            'expires_in': 300
        }, format='json')

        self.assertEqual(init_res.status_code, status.HTTP_201_CREATED)
        session_id = init_res.data['pairing_session_id']
        self.assertEqual(init_res.data['status'], 'PENDING_VERIFICATION')

        # 2. Web user submits PIN
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')
        verify_res = self.client.post(self.verify_url, {
            'pin': '849-201' # formatted input
        }, format='json')

        self.assertEqual(verify_res.status_code, status.HTTP_200_OK)
        self.assertEqual(verify_res.data['status'], PairingStatus.AWAITING_APPROVAL)

        # 3. Android phone user physically approves
        self.client.credentials() # Clear bearer auth to simulate device call
        approve_res = self.client.post(self.approve_url, {
            'pairing_session_id': session_id,
            'device_token': self.raw_device_token,
            'approved': True
        }, format='json')

        self.assertEqual(approve_res.status_code, status.HTTP_200_OK)
        self.assertEqual(approve_res.data['status'], 'PAIRED')

        # Verify device ownership in DB
        self.device.refresh_from_db()
        self.assertEqual(self.device.status, DeviceStatus.PAIRED)
        self.assertEqual(self.device.user, self.user)

    def test_pin_brute_force_lockout_after_3_attempts(self):
        # Device initiates pairing
        init_res = self.client.post(self.initiate_url, {
            'device_id': str(self.device.id),
            'device_token': self.raw_device_token,
            'pin_hash': self.test_pin_hash,
            'pin_salt': self.test_salt,
            'expires_in': 300
        }, format='json')
        session_id = init_res.data['pairing_session_id']

        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')

        # Attempt 1: Wrong PIN
        res1 = self.client.post(self.verify_url, {'pin': '111111'}, format='json')
        self.assertEqual(res1.status_code, status.HTTP_400_BAD_REQUEST)

        # Attempt 2: Wrong PIN
        res2 = self.client.post(self.verify_url, {'pin': '222222'}, format='json')
        self.assertEqual(res2.status_code, status.HTTP_400_BAD_REQUEST)

        # Attempt 3: Wrong PIN
        res3 = self.client.post(self.verify_url, {'pin': '333333'}, format='json')
        self.assertEqual(res3.status_code, status.HTTP_400_BAD_REQUEST)

        # Attempt 4: Even with correct PIN, session must now be failed/locked out
        res4 = self.client.post(self.verify_url, {'pin': self.test_pin}, format='json')
        self.assertEqual(res4.status_code, status.HTTP_400_BAD_REQUEST)

        session = PairingSession.objects.get(id=session_id)
        self.assertEqual(session.status, PairingStatus.FAILED)
