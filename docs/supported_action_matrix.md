# Android Remote Control: Supported Action Matrix & OS Security Boundaries

## 1. Overview
This matrix provides a technical and legal analysis of supported remote interaction capabilities via standard, user-approved Android APIs (`AccessibilityService`, `MediaProjection`, and `WindowManager`).

The platform **strictly adheres** to the Android Security Sandbox and does not rely on root access, undocumented private APIs, or developer-only ADB daemon workarounds.

---

## 2. Interaction Capability Matrix

| Action / Gesture | Support Status | API Level | Technical Mechanism | Operating System Restrictions & Guardrails |
| :--- | :---: | :---: | :--- | :--- |
| **Tap / Single Click** | **Full** | Android 7.0+ (API 24+) | `dispatchGesture()` with single-point `StrokeDescription` (40–60ms). | Works across standard Android applications and home launchers. Blocked over system permission dialogs on Android 12+. |
| **Double Tap** | **Full** | Android 7.0+ (API 24+) | Two sequential `dispatchGesture()` strokes with 100ms interval. | Fully supported. |
| **Long Press** | **Full** | Android 7.0+ (API 24+) | `dispatchGesture()` held at coordinates for 750–1000ms. | Triggers context menus, app shortcut actions, and drag handles. |
| **Swipe / Drag** | **Full** | Android 7.0+ (API 24+) | `dispatchGesture()` with multi-point continuous `Path`. | Fully supported for home screen pagination, photo galleries, and map panning. |
| **Smooth Scrolling** | **Full** | Android 7.0+ (API 24+) | Mouse wheel translated to small directional swipe gestures. | Fluidly scrolls lists (`RecyclerView`, `ScrollView`, WebViews). |
| **Back Navigation** | **Full** | Android 4.0+ (API 14+) | `performGlobalAction(GLOBAL_ACTION_BACK)` | Replicates physical/gesture back button across all standard views. |
| **Home Navigation** | **Full** | Android 4.0+ (API 14+) | `performGlobalAction(GLOBAL_ACTION_HOME)` | Minimizes foreground applications and returns to default launcher. |
| **Recents / Overview** | **Full** | Android 4.0+ (API 16+) | `performGlobalAction(GLOBAL_ACTION_RECENTS)` | Opens the recent apps carousel. |
| **Notifications Shade**| **Full** | Android 4.0+ (API 14+) | `performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)` | Expands the Android notification drawer. |
| **Quick Settings** | **Full** | Android 4.2+ (API 17+) | `performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)` | Expands Wi-Fi, Bluetooth, and device toggles. |
| **Lock Screen (Turn Off)** | **Supported** | Android 9.0+ (API 28+) | `performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)` | Turns off the display and engages device keyguard. |
| **Text Entry (Typing)** | **Conditional** | Android 5.0+ (API 21+) | `AccessibilityNodeInfo.ACTION_SET_TEXT` on active focused node. | Injects text into standard Android `EditText` views. **Does not work** on custom non-accessible game canvases (e.g., Unity/Unreal) or password fields that hide their node tree. |
| **Physical Keyboard Emulation** | **Restricted** | Requires System Privileges | `InputManager.injectInputEvent()` requires `INJECT_EVENTS` (system signature permission). | Standard non-system apps cannot synthesize raw hardware keystrokes unless packaged as an Input Method Editor (IME soft keyboard). |
| **Device Unlock (PIN / Pattern)** | **Restricted** | Android Security Sandbox | Keyguard security barrier blocks accessibility gestures. | Android explicitly prohibits synthetic accessibility input over PIN/Pattern/Biometric lock screens to prevent brute force and theft bypass. |
| **System Permission Dialogs** | **Restricted** | Android 12+ (API 31+) | Tapjacking mitigation flag `FLAG_WINDOW_IS_OBSCURED`. | Android blocks accessibility gestures over OS permission requests (e.g., "Allow Camera/Microphone"). User must physically tap their device. |
| **Protected Applications** | **Restricted** | Android OS Compositor | `WindowManager.LayoutParams.FLAG_SECURE`. | Screen streaming turns black on banking apps, password managers, and DRM media (Netflix). This is an inviolable Android privacy boundary. |

---

## 3. Coordinate Normalization & Orientation Math

To account for screen rotation without distorting pointer accuracy, normalized client coordinates $(x_n, y_n) \in [0.0, 1.0]$ are transformed according to the active display rotation:

$$\begin{aligned}
\text{Rotation } 0^\circ \text{ (Portrait):} \quad & x_p = x_n \cdot W, \quad y_p = y_n \cdot H \\
\text{Rotation } 90^\circ \text{ (Landscape):} \quad & x_p = (1 - y_n) \cdot W, \quad y_p = x_n \cdot H \\
\text{Rotation } 180^\circ \text{ (Inverted Portrait):} \quad & x_p = (1 - x_n) \cdot W, \quad y_p = (1 - y_n) \cdot H \\
\text{Rotation } 270^\circ \text{ (Reverse Landscape):} \quad & x_p = y_n \cdot W, \quad y_p = (1 - x_n) \cdot H
\end{aligned}$$

Where $W$ and $H$ represent the physical pixel dimensions of the current Android window metrics.

---

## 4. Input Rate Limiting and Sanitization

- **Rate Limiting**: Input events arriving faster than $40 \text{ Hz}$ (under $25\text{ms}$ interval) are safely throttled to avoid filling Android's internal `dispatchGesture` queue.
- **Bounds Clamping**: All coordinates are strictly clamped to $[0.0, 1.0]$ before pixel conversion, preventing negative or out-of-screen touch dispatch errors.
