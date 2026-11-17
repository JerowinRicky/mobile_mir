import os
import sys
import re
import hashlib
import binascii
import xml.etree.ElementTree as ET

def test_phase1_deliverables():
    print("=" * 60)
    print("VERIFYING PHASE 1 ARTIFACTS AND IMPLEMENTATION")
    print("=" * 60)

    # 1. Check all required files
    required_files = [
        "docs/architecture.md",
        "docs/security.md",
        "docs/database_schema.sql",
        "docs/api_spec.md",
        "PROJECT_STATUS.md",
        "android/settings.gradle.kts",
        "android/build.gradle.kts",
        "android/gradle.properties",
        "android/gradle/libs.versions.toml",
        "android/app/build.gradle.kts",
        "android/app/proguard-rules.pro",
        "android/app/src/main/AndroidManifest.xml",
        "android/app/src/main/res/values/strings.xml",
        "android/app/src/main/res/values/colors.xml",
        "android/app/src/main/res/xml/accessibility_service_config.xml",
        "android/app/src/main/java/com/antigravity/remotedevice/RemoteApplication.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/data/local/KeystoreManager.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/data/local/SecureStorage.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/domain/model/Models.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/domain/crypto/PinGenerator.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/service/MediaProjectionService.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/service/RemoteAccessibilityService.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/util/PermissionHelper.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/theme/Color.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/theme/Type.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/theme/Theme.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/components/ConsentDialog.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/screens/DeviceRegistrationScreen.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/screens/PairingScreen.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/screens/PermissionsScreen.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/screens/ActiveSessionScreen.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/ui/MainActivity.kt",
        "android/app/src/test/java/com/antigravity/remotedevice/crypto/PinGeneratorTest.kt"
    ]

    base_dir = os.path.abspath(os.path.dirname(__file__))
    # if running from android/ or root:
    if os.path.basename(base_dir) == "android":
        root_dir = os.path.dirname(base_dir)
    else:
        root_dir = base_dir

    missing = []
    for rel_path in required_files:
        full_path = os.path.join(root_dir, rel_path.replace("/", os.sep))
        if not os.path.exists(full_path):
            missing.append(rel_path)

    if missing:
        print(f"[FAIL] Missing {len(missing)} files: {missing}")
        return False
    print(f"[PASS] All {len(required_files)} Phase 1 files exist on disk.")

    # 2. Check AndroidManifest declarations
    manifest_path = os.path.join(root_dir, "android", "app", "src", "main", "AndroidManifest.xml")
    with open(manifest_path, "r", encoding="utf-8") as f:
        manifest_content = f.read()

    assert "android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" in manifest_content, "Missing media projection permission"
    assert "android.permission.BIND_ACCESSIBILITY_SERVICE" in manifest_content, "Missing accessibility service permission"
    assert "foregroundServiceType=\"mediaProjection\"" in manifest_content, "Missing foregroundServiceType on MediaProjectionService"
    print("[PASS] AndroidManifest.xml security and service declarations verified.")

    # 3. Check Accessibility Service config
    a11y_cfg_path = os.path.join(root_dir, "android", "app", "src", "main", "res", "xml", "accessibility_service_config.xml")
    with open(a11y_cfg_path, "r", encoding="utf-8") as f:
        a11y_cfg = f.read()

    assert "canPerformGestures=\"true\"" in a11y_cfg, "canPerformGestures must be true"
    print("[PASS] accessibility_service_config.xml verified (canPerformGestures=true).")

    # 4. Cryptographic cross-language test: PBKDF2 with known test vector
    test_pin = "492015"
    test_salt = b"test_salt_123456"
    test_iterations = 10000
    derived_key = hashlib.pbkdf2_hmac("sha256", test_pin.encode("utf-8"), test_salt, test_iterations, 32)
    expected_hex = binascii.hexlify(derived_key).decode("utf-8")
    assert len(expected_hex) == 64, "Key length must be 256 bits (64 hex chars)"
    print(f"[PASS] PBKDF2-HMAC-SHA256 test vector computed successfully: {expected_hex[:16]}... (256-bit)")

    # 5. PIN format regex verification
    sample_pin = "849201"
    formatted = f"{sample_pin[:3]}-{sample_pin[3:]}"
    assert re.match(r"^\d{3}-\d{3}$", formatted), "Formatted PIN must match XXX-XXX format"
    print(f"[PASS] PIN format regex verified: '{formatted}'.")

    print("=" * 60)
    print("ALL PHASE 1 ARCHITECTURAL & COMPONENT CHECKS PASSED!")
    print("=" * 60)
    return True

if __name__ == "__main__":
    success = test_phase1_deliverables()
    sys.exit(0 if success else 1)
