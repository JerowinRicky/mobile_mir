import os
import sys
import hashlib
import binascii
import hmac

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'

import django
django.setup()

from django.test import RequestFactory
from rest_framework import status
from apps.accounts.models import User
from apps.accounts.authentication import generate_access_token, generate_refresh_token, verify_token
from apps.devices.models import Device, DeviceStatus
from apps.devices.views import DeviceRegisterView
from apps.pairing.models import PairingSession, PairingStatus
from apps.pairing.services import PairingService, compute_pbkdf2
from apps.sessions.models import RemoteSession, SessionStatus
from apps.sessions.services import generate_turn_credentials
from apps.audit.models import AuditLog

def run_phase2_verification():
    print("=" * 65)
    print("RUNNING PHASE 2 COMPREHENSIVE BACKEND & PAIRING VERIFICATION")
    print("=" * 65)

    rf = RequestFactory()

    # 1. User Authentication & JWT Lifecycle
    print("[TEST 1] Testing User Registration and Password Hashing...")
    email = "phase2_verifier@remotedevice.internal"
    User.objects.filter(email=email).delete()
    user = User.objects.create_user(email=email, password="StrongPassword2026!", first_name="Phase2", last_name="Tester")
    assert user.check_password("StrongPassword2026!"), "Password hash verification failed"
    assert not user.check_password("WrongPassword"), "Password check must reject invalid password"
    print(f"  -> User created with UUID {user.id}. Password hashed with Argon2/PBKDF2.")

    print("[TEST 2] Testing Short-Lived JWT Access & Refresh Token Rotation...")
    access_tok = generate_access_token(user)
    refresh_tok = generate_refresh_token(user)
    access_payload = verify_token(access_tok, expected_type='access')
    refresh_payload = verify_token(refresh_tok, expected_type='refresh')
    assert access_payload['sub'] == str(user.id), "Access token sub mismatch"
    assert refresh_payload['sub'] == str(user.id), "Refresh token sub mismatch"
    print(f"  -> JWT access token verified (TTL: {access_payload['exp'] - access_payload['iat']}s).")

    # 2. Device Registration & Hardware-backed Keys
    print("[TEST 3] Testing Device Registration Foundation & Token Hashing...")
    hw_fingerprint = "hw-fingerprint-phase2-suite-001"
    Device.objects.filter(hardware_fingerprint=hw_fingerprint).delete()
    dev_reg_payload = {
        'hardware_fingerprint': hw_fingerprint,
        'device_name': 'Pixel 8 Pro (Phase 2 Test)',
        'device_model': 'Pixel 8 Pro',
        'android_version': '14.0',
        'app_version': '1.0.0',
        'public_key_pem': '-----BEGIN PUBLIC KEY-----\nMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEABC\n-----END PUBLIC KEY-----'
    }
    request = rf.post('/api/v1/devices/register/', dev_reg_payload, content_type='application/json')
    response = DeviceRegisterView.as_view()(request)
    assert response.status_code == status.HTTP_201_CREATED, f"Device registration failed: {response.data}"
    device_id = response.data['device_id']
    device_token = response.data['device_token']
    device = Device.objects.get(id=device_id)
    assert device.status == DeviceStatus.UNPAIRED, "Device initial status must be UNPAIRED"
    expected_token_hash = hashlib.sha256(device_token.encode('utf-8')).hexdigest()
    assert device.device_token_hash == expected_token_hash, "Device token hash mismatch in DB"
    print(f"  -> Device registered: {device.device_name} (Status: {device.status}). Token hashed.")

    # 3. Ephemeral PIN Pairing Flow
    print("[TEST 4] Testing Cryptographic Temporary PIN Generation & PBKDF2 Verification...")
    test_pin = "739104"
    salt_hex = binascii.hexlify(os.urandom(16)).decode('utf-8')
    pin_hash = compute_pbkdf2(test_pin, salt_hex)

    session = PairingService.initiate_pairing(device=device, pin_hash=pin_hash, pin_salt=salt_hex, expires_in_seconds=300)
    assert session.status == PairingStatus.PENDING_VERIFICATION, "Session status must be PENDING_VERIFICATION"
    print(f"  -> Pairing session {session.id} initiated. Expires at {session.expires_at}.")

    # Verify PIN with formatted input
    success, msg, matched_session = PairingService.verify_pin(user=user, raw_pin="739-104", client_ip="192.168.1.50")
    assert success, f"PIN verification failed: {msg}"
    assert matched_session.status == PairingStatus.AWAITING_APPROVAL, "Session status must be AWAITING_APPROVAL"
    assert matched_session.target_user == user, "Target user must match"
    print(f"  -> PIN '739-104' verified via PBKDF2. Session state: {matched_session.status}.")

    # 4. Device Physical Approval
    print("[TEST 5] Testing Device Physical Confirmation & Ownership Association...")
    approve_success, approve_msg = PairingService.approve_pairing(
        session_id=str(session.id),
        device=device,
        approved=True
    )
    assert approve_success, f"Device approval failed: {approve_msg}"
    device.refresh_from_db()
    session.refresh_from_db()
    assert device.status == DeviceStatus.PAIRED, "Device status must be PAIRED"
    assert device.user == user, "Device owner must be user"
    assert session.status == PairingStatus.APPROVED, "Session status must be APPROVED"
    print(f"  -> Device approved physically. Device owner: {device.user.email} (Status: {device.status}).")

    # 5. PIN Brute-Force Lockout Enforcement
    print("[TEST 6] Testing Brute-Force Protection (Max 3 Attempts Lockout)...")
    brute_pin = "123456"
    brute_salt = binascii.hexlify(os.urandom(16)).decode('utf-8')
    brute_hash = compute_pbkdf2(brute_pin, brute_salt)
    brute_session = PairingService.initiate_pairing(device=device, pin_hash=brute_hash, pin_salt=brute_salt)

    for attempt in range(1, 4):
        s, m, _ = PairingService.verify_pin(user=user, raw_pin="999999")
        assert not s, f"Attempt {attempt} should fail"

    # 4th attempt with CORRECT PIN must still be rejected due to lockout
    s, m, _ = PairingService.verify_pin(user=user, raw_pin=brute_pin)
    assert not s, "4th attempt should be rejected even with valid PIN"
    brute_session.refresh_from_db()
    assert brute_session.status == PairingStatus.FAILED, f"Brute session must be FAILED, got {brute_session.status}"
    print(f"  -> Brute force lockout verified: Session marked FAILED after 3 attempts.")

    # 6. WebRTC Session & Coturn Ephemeral Credentials
    print("[TEST 7] Testing WebRTC Session Lifecycle & Coturn Ephemeral Credentials...")
    turn_creds = generate_turn_credentials(str(user.id))
    ice_servers = turn_creds['ice_servers']
    assert len(ice_servers) >= 2, "Must return STUN and TURN configurations"
    turn_server = ice_servers[1]
    assert 'username' in turn_server and 'credential' in turn_server, "Missing TURN credentials"
    assert ':' in turn_server['username'], "TURN username format invalid"
    print(f"  -> Coturn credentials generated (Username: {turn_server['username']}, TTL: {turn_creds['ttl_seconds']}s).")

    remote_session = RemoteSession.objects.create(
        user=user,
        device=device,
        session_token="sec_token_suite_999",
        status=SessionStatus.INITIALIZING
    )
    assert remote_session.status == SessionStatus.INITIALIZING
    remote_session.terminate(reason="Test Graceful Disconnect")
    assert remote_session.status == SessionStatus.TERMINATED
    assert remote_session.duration_seconds is not None
    print(f"  -> Remote session created and terminated (Duration: {remote_session.duration_seconds}s).")

    # 7. Audit Logging
    print("[TEST 8] Verifying Security Audit Log Records...")
    audit_count = AuditLog.objects.filter(device_id=device.id).count()
    assert audit_count >= 1, "Audit logs must record device events"
    print(f"  -> Verified {audit_count} audit log entries for device {device.id}.")

    # 8. Postman Collection
    print("[TEST 9] Verifying Postman Collection File...")
    postman_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "postman_collection.json")
    assert os.path.exists(postman_path), "postman_collection.json must exist"
    print("  -> Postman collection exists and is ready for import.")

    print("=" * 65)
    print("ALL 9 PHASE 2 END-TO-END BACKEND & PAIRING VERIFICATIONS PASSED!")
    print("=" * 65)
    return True

if __name__ == '__main__':
    success = run_phase2_verification()
    sys.exit(0 if success else 1)
