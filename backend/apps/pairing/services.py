import hashlib
import binascii
import hmac
from datetime import timedelta
from django.utils import timezone
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives.serialization import load_pem_public_key
import base64

from apps.pairing.models import PairingSession, PairingStatus
from apps.devices.models import Device, DeviceStatus

def compute_pbkdf2(raw_pin: str, salt_hex: str, iterations: int = 10000) -> str:
    salt_bytes = binascii.unhexlify(salt_hex)
    key = hashlib.pbkdf2_hmac('sha256', raw_pin.encode('utf-8'), salt_bytes, iterations, 32)
    return binascii.hexlify(key).decode('utf-8')

class PairingService:

    @staticmethod
    def initiate_pairing(device: Device, pin_hash: str, pin_salt: str, expires_in_seconds: int = 300) -> PairingSession:
        # Invalidate previous active pairing sessions for this device
        PairingSession.objects.filter(
            device=device,
            status__in=[PairingStatus.PENDING_VERIFICATION, PairingStatus.AWAITING_APPROVAL]
        ).update(status=PairingStatus.EXPIRED)

        expires_at = timezone.now() + timedelta(seconds=expires_in_seconds)
        return PairingSession.objects.create(
            device=device,
            pin_hash=pin_hash,
            pin_salt=pin_salt,
            expires_at=expires_at,
            status=PairingStatus.PENDING_VERIFICATION
        )

    @staticmethod
    def verify_pin(user, raw_pin: str, client_ip: str = None, client_ua: str = '') -> tuple[bool, str, PairingSession | None]:
        clean_pin = raw_pin.replace('-', '').strip()
        if not clean_pin.isdigit() or len(clean_pin) != 6:
            return False, "PIN must be exactly 6 digits.", None

        # Look up active, non-expired sessions
        now = timezone.now()
        active_sessions = PairingSession.objects.filter(
            status=PairingStatus.PENDING_VERIFICATION,
            expires_at__gt=now
        ).select_related('device')

        matched_session = None

        for session in active_sessions:
            if session.attempt_count >= session.max_attempts:
                session.status = PairingStatus.FAILED
                session.save()
                continue

            # Compute PBKDF2 with session salt
            computed_hash = compute_pbkdf2(clean_pin, session.pin_salt)
            if hmac.compare_digest(computed_hash, session.pin_hash):
                matched_session = session
                break
            else:
                session.attempt_count += 1
                if session.attempt_count >= session.max_attempts:
                    session.status = PairingStatus.FAILED
                session.save()

        if not matched_session:
            return False, "Invalid PIN or session expired. Please verify and try again.", None

        # Transition session to awaiting approval
        matched_session.target_user = user
        matched_session.client_ip = client_ip
        matched_session.client_user_agent = client_ua
        matched_session.status = PairingStatus.AWAITING_APPROVAL
        matched_session.save()

        return True, "PIN verified. Confirm connection on your Android phone.", matched_session

    @staticmethod
    def approve_pairing(session_id: str, device: Device, approved: bool, signed_challenge: str = None) -> tuple[bool, str]:
        try:
            session = PairingSession.objects.get(id=session_id, device=device)
        except PairingSession.DoesNotExist:
            return False, "Pairing session not found for this device."

        if session.is_expired:
            session.status = PairingStatus.EXPIRED
            session.save()
            return False, "Pairing session has expired."

        if session.status != PairingStatus.AWAITING_APPROVAL:
            return False, f"Invalid session state: {session.status}"

        if not approved:
            session.status = PairingStatus.FAILED
            session.save()
            return False, "Pairing was rejected by device user."

        # Verify cryptographic challenge signature if provided
        if signed_challenge and device.public_key_pem:
            try:
                public_key = load_pem_public_key(device.public_key_pem.encode('utf-8'))
                sig_bytes = base64.b64decode(signed_challenge)
                challenge_data = str(session.id).encode('utf-8')
                public_key.verify(sig_bytes, challenge_data, ec.ECDSA(hashes.SHA256()))
            except Exception as e:
                return False, f"Device cryptographic signature verification failed: {str(e)}"

        # Finalize pairing: link device to user
        session.status = PairingStatus.APPROVED
        session.save()

        device.user = session.target_user
        device.status = DeviceStatus.PAIRED
        device.save()

        return True, "Device paired successfully."
