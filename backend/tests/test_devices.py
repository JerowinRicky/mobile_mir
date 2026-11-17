from django.test import TestCase
from rest_framework.test import APIClient
from rest_framework import status
from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token
from apps.devices.models import Device, DeviceStatus

class DevicesTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.register_url = '/api/v1/devices/register/'
        self.list_url = '/api/v1/devices/'

        self.user = User.objects.create_user(email='bob@example.com', password='StrongPassword2026!')
        self.user_token = generate_access_token(self.user)

        self.device_payload = {
            'hardware_fingerprint': 'test-hw-fingerprint-999',
            'device_name': 'Pixel 8 Pro',
            'device_model': 'Google Pixel 8 Pro',
            'android_version': '14.0',
            'app_version': '1.0.0',
            'public_key_pem': '-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n-----END PUBLIC KEY-----'
        }

    def test_device_registration(self):
        response = self.client.post(self.register_url, self.device_payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        self.assertIn('device_id', response.data)
        self.assertIn('device_token', response.data)
        self.assertEqual(response.data['status'], 'UNPAIRED')

        # Check in DB
        device = Device.objects.get(id=response.data['device_id'])
        self.assertEqual(device.device_name, 'Pixel 8 Pro')
        self.assertNotEqual(device.device_token_hash, '')

    def test_device_registration_invalid_public_key_fails(self):
        bad_payload = self.device_payload.copy()
        bad_payload['public_key_pem'] = 'invalid_key_data'
        response = self.client.post(self.register_url, bad_payload, format='json')
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_device_revocation(self):
        reg_response = self.client.post(self.register_url, self.device_payload, format='json')
        device_id = reg_response.data['device_id']

        device = Device.objects.get(id=device_id)
        device.user = self.user
        device.status = DeviceStatus.PAIRED
        device.save()

        # Call revoke endpoint
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')
        revoke_url = f'/api/v1/devices/{device_id}/revoke/'
        revoke_res = self.client.post(revoke_url)

        self.assertEqual(revoke_res.status_code, status.HTTP_200_OK)
        device.refresh_from_db()
        self.assertEqual(device.status, DeviceStatus.REVOKED)
        self.assertIsNone(device.user)
