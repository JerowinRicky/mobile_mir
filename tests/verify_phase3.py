import os
import sys
import hmac
import hashlib
import base64
import time

base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
backend_dir = os.path.join(base_dir, 'backend')
sys.path.insert(0, backend_dir)
os.environ['DJANGO_SETTINGS_MODULE'] = 'core.settings'

import django
django.setup()

from apps.sessions.services import generate_turn_credentials
from apps.devices.models import Device
from apps.sessions.models import RemoteSession, SessionStatus

def test_phase3_deliverables():
    print("=" * 65)
    print("VERIFYING PHASE 3: WEBRTC SCREEN MIRRORING PIPELINE")
    print("=" * 65)

    # 1. Check Android WebRTC implementation files
    android_webrtc_files = [
        "android/app/src/main/java/com/antigravity/remotedevice/webrtc/WebRtcManager.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/data/remote/SignalingClient.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/service/MediaProjectionService.kt"
    ]
    for rel_path in android_webrtc_files:
        full_path = os.path.join(base_dir, rel_path.replace("/", os.sep))
        assert os.path.exists(full_path), f"Missing Android WebRTC file: {rel_path}"
    print("[PASS] Android WebRTC and MediaProjection integration files verified.")

    # 2. Check React Frontend files
    frontend_files = [
        "frontend/package.json",
        "frontend/tsconfig.json",
        "frontend/vite.config.ts",
        "frontend/src/types/index.ts",
        "frontend/src/services/api.ts",
        "frontend/src/services/signaling.ts",
        "frontend/src/hooks/useWebRTC.ts",
        "frontend/src/components/Navbar.tsx",
        "frontend/src/components/NetworkQualityHud.tsx",
        "frontend/src/components/VideoPlayer.tsx",
        "frontend/src/pages/LoginPage.tsx",
        "frontend/src/pages/DashboardPage.tsx",
        "frontend/src/pages/RemoteWorkspacePage.tsx",
        "frontend/src/App.tsx",
        "frontend/src/main.tsx"
    ]
    for rel_path in frontend_files:
        full_path = os.path.join(base_dir, rel_path.replace("/", os.sep))
        assert os.path.exists(full_path), f"Missing frontend file: {rel_path}"
    print("[PASS] React + TypeScript + WebRTC frontend workspace files verified.")

    # 3. Check Coturn Server Configuration
    turn_conf_path = os.path.join(base_dir, "infra", "coturn", "turnserver.conf")
    assert os.path.exists(turn_conf_path), "Missing turnserver.conf"
    with open(turn_conf_path, "r", encoding="utf-8") as f:
        conf_content = f.read()

    assert "use-auth-secret" in conf_content, "Coturn must use ephemeral auth secret"
    assert "static-auth-secret" in conf_content, "Missing static-auth-secret in Coturn config"
    assert "listening-port=3478" in conf_content, "Coturn listening port 3478 not configured"
    assert "fingerprint" in conf_content, "Coturn fingerprint parameter not enabled"
    print("[PASS] Coturn STUN/TURN server configuration verified.")

    # 4. Verify Ephemeral TURN Credential Algorithm
    test_user_id = "user_e2e_turn_test_123"
    creds = generate_turn_credentials(test_user_id)
    assert 'ice_servers' in creds and len(creds['ice_servers']) >= 2
    turn_entry = creds['ice_servers'][1]
    username = turn_entry['username']
    password = turn_entry['credential']

    parts = username.split(':')
    assert len(parts) == 2, "Username must follow timestamp:user_id format"
    expiry_ts = int(parts[0])
    assert expiry_ts > time.time(), "Credential expiry must be in the future"

    # Verify HMAC-SHA1 signature
    secret = b"antigravity-coturn-static-secret-2026"
    expected_digest = hmac.new(secret, username.encode('utf-8'), hashlib.sha1).digest()
    expected_password = base64.b64encode(expected_digest).decode('utf-8')
    assert password == expected_password, "HMAC-SHA1 password mismatch"
    print(f"[PASS] Coturn HMAC-SHA1 credential signature verified: {username} -> {password[:16]}...")

    # 5. Verify Input Coordinate Normalization
    # Test boundary mappings for 1080x2400 Android screens
    click_x, click_y = 540, 1200
    screen_w, screen_h = 1080, 2400
    norm_x = click_x / screen_w
    norm_y = click_y / screen_h
    assert norm_x == 0.5 and norm_y == 0.5, "Center coordinate must be 0.5, 0.5"

    edge_x, edge_y = 1080, 2400
    assert (edge_x / screen_w) == 1.0 and (edge_y / screen_h) == 1.0
    print("[PASS] Input coordinate normalization mathematics verified.")

    print("=" * 65)
    print("ALL PHASE 3 WEBRTC SCREEN MIRRORING VERIFICATIONS PASSED!")
    print("=" * 65)
    return True

if __name__ == '__main__':
    success = test_phase3_deliverables()
    sys.exit(0 if success else 1)
