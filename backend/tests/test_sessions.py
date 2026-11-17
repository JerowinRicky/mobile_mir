from django.test import TestCase
from rest_framework.test import APIClient
from rest_framework import status
from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token
from apps.devices.models import Device, DeviceStatus
from apps.sessions.models import RemoteSession, SessionStatus
from apps.audit.models import AuditLog

class RemoteSessionTestCase(TestCase):
    def setUp(self):
        self.client = APIClient()
        self.user = User.objects.create_user(email='david@example.com', password='StrongPassword2026!')
        self.user_token = generate_access_token(self.user)
        self.client.credentials(HTTP_AUTHORIZATION=f'Bearer {self.user_token}')

        self.device = Device.objects.create(
            user=self.user,
            hardware_fingerprint='device-hw-session-test',
            device_name='OnePlus 12',
            public_key_pem='-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE\n-----END PUBLIC KEY-----',
            device_token_hash='sample_hash',
            status=DeviceStatus.PAIRED
        )

    def test_create_and_terminate_session(self):
        # 1. Create Session
        create_res = self.client.post('/api/v1/sessions/create/', {
            'device_id': str(self.device.id)
        }, format='json')

        self.assertEqual(create_res.status_code, status.HTTP_201_CREATED)
        self.assertIn('session', create_res.data)
        self.assertIn('turn_config', create_res.data)

        session_id = create_res.data['session']['id']
        turn_servers = create_res.data['turn_config']['ice_servers']
        self.assertTrue(len(turn_servers) >= 2) # STUN and TURN present

        # 2. Verify Session exists in DB
        session = RemoteSession.objects.get(id=session_id)
        self.assertEqual(session.status, SessionStatus.INITIALIZING)

        # 3. Terminate Session
        term_res = self.client.post(f'/api/v1/sessions/{session_id}/terminate/', {
            'reason': 'Test Terminate'
        }, format='json')

        self.assertEqual(term_res.status_code, status.HTTP_200_OK)
        session.refresh_from_db()
        self.assertEqual(session.status, SessionStatus.TERMINATED)
        self.assertEqual(session.termination_reason, 'Test Terminate')

        # 4. Verify Audit Log recorded
        audit_events = AuditLog.objects.filter(user=self.user)
        self.assertTrue(audit_events.filter(event_type='SESSION_CREATE').exists())
        self.assertTrue(audit_events.filter(event_type='SESSION_TERMINATE').exists())
