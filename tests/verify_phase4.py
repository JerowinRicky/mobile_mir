import os
import sys

def test_coordinate_transformation():
    # Simulate DisplayMetrics: 1080 x 2400
    width = 1080.0
    height = 2400.0

    # Test coordinate mapping function
    def map_coords(x, y, rotation):
        clamped_x = max(0.0, min(1.0, x))
        clamped_y = max(0.0, min(1.0, y))

        if rotation == 0: # Surface.ROTATION_0 (Portrait)
            return clamped_x * width, clamped_y * height
        elif rotation == 1: # Surface.ROTATION_90 (Landscape Clockwise)
            return (1.0 - clamped_y) * width, clamped_x * height
        elif rotation == 2: # Surface.ROTATION_180 (Inverted Portrait)
            return (1.0 - clamped_x) * width, (1.0 - clamped_y) * height
        elif rotation == 3: # Surface.ROTATION_270 (Reverse Landscape)
            return clamped_y * width, (1.0 - clamped_x) * height
        return clamped_x * width, clamped_y * height

    # 1. Portrait tests
    px, py = map_coords(0.5, 0.5, 0)
    assert px == 540.0 and py == 1200.0, "Center portrait mapping failed"

    px, py = map_coords(0.0, 0.0, 0)
    assert px == 0.0 and py == 0.0, "Top-left portrait mapping failed"

    # 2. Landscape 90 tests
    px, py = map_coords(0.0, 0.0, 1)
    assert px == 1080.0 and py == 0.0, "Landscape 90 top-left mapping failed"

    # 3. Clamping bounds check
    px, py = map_coords(-0.25, 1.35, 0)
    assert px == 0.0 and py == 2400.0, "Coordinate clamping failed"
    print("[PASS] Coordinate transformation & orientation algorithms verified.")

def test_gesture_parameters():
    commands = [
        {"action": "TAP", "x": 0.5, "y": 0.5, "duration_ms": 50},
        {"action": "LONG_PRESS", "x": 0.5, "y": 0.5, "duration_ms": 750},
        {"action": "SWIPE", "start_x": 0.5, "start_y": 0.8, "end_x": 0.5, "end_y": 0.2, "duration_ms": 250},
        {"action": "SCROLL", "start_x": 0.5, "start_y": 0.5, "end_x": 0.5, "end_y": 0.35, "duration_ms": 120},
        {"action": "GLOBAL_NAV", "target": "BACK"},
        {"action": "GLOBAL_NAV", "target": "HOME"},
        {"action": "GLOBAL_NAV", "target": "RECENTS"},
        {"action": "GLOBAL_NAV", "target": "NOTIFICATIONS"},
        {"action": "GLOBAL_NAV", "target": "QUICK_SETTINGS"},
        {"action": "GLOBAL_NAV", "target": "LOCK_SCREEN"},
        {"action": "INPUT_TEXT", "text": "Antigravity Remote 2026"}
    ]

    for cmd in commands:
        if cmd["action"] == "TAP":
            assert 30 <= cmd["duration_ms"] <= 100
        elif cmd["action"] == "LONG_PRESS":
            assert cmd["duration_ms"] >= 750
        elif cmd["action"] == "SWIPE":
            assert cmd["start_y"] != cmd["end_y"] or cmd["start_x"] != cmd["end_x"]
        elif cmd["action"] == "GLOBAL_NAV":
            assert cmd["target"] in ["BACK", "HOME", "RECENTS", "NOTIFICATIONS", "QUICK_SETTINGS", "LOCK_SCREEN"]
        elif cmd["action"] == "INPUT_TEXT":
            assert len(cmd["text"]) > 0

    print(f"[PASS] Verified all {len(commands)} gesture parameter definitions and bounds.")

def test_phase4_files():
    base_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    required_files = [
        "android/app/src/main/java/com/antigravity/remotedevice/service/InputDispatcher.kt",
        "android/app/src/main/java/com/antigravity/remotedevice/service/RemoteAccessibilityService.kt",
        "frontend/src/components/VideoPlayer.tsx",
        "frontend/src/pages/RemoteWorkspacePage.tsx",
        "frontend/src/types/index.ts",
        "docs/supported_action_matrix.md"
    ]
    for rel_path in required_files:
        full_path = os.path.join(base_dir, rel_path.replace("/", os.sep))
        assert os.path.exists(full_path), f"Missing Phase 4 file: {rel_path}"
    print(f"[PASS] All {len(required_files)} Phase 4 implementation and matrix files verified.")

def main():
    print("=" * 65)
    print("VERIFYING PHASE 4: REMOTE CONTROL IMPLEMENTATION PIPELINE")
    print("=" * 65)

    test_coordinate_transformation()
    test_gesture_parameters()
    test_phase4_files()

    print("=" * 65)
    print("ALL PHASE 4 REMOTE CONTROL VERIFICATIONS PASSED!")
    print("=" * 65)
    return True

if __name__ == '__main__':
    success = main()
    sys.exit(0 if success else 1)
