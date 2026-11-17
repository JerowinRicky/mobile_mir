# Android Production Release Build & Signing Guide

**Application ID**: `com.antigravity.remotedevice`  
**Target SDK**: Android 14 (API level 34)  
**Minimum SDK**: Android 8.0 (API level 26)  
**Language / Framework**: Kotlin 1.9.22 + Jetpack Compose  

---

## 1. Release Keystore Generation

Create a dedicated hardware-strength RSA or EC signing key for production APK / AAB signing:

```bash
# Generate 4096-bit RSA release keystore
keytool -genkeypair -v \
  -keystore release.keystore \
  -alias remotedevice \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -dname "CN=RemoteDevice, OU=Mobile Security, O=Antigravity, L=San Francisco, ST=California, C=US"
```

> [!CAUTION]
> Back up `release.keystore` securely in an encrypted vault (e.g., AWS Secrets Manager or HashiCorp Vault). Never commit `release.keystore` into Git source control.

---

## 2. Environment Variables for CI/CD Signing

Set the following environment variables on your build machine or GitHub Actions / GitLab CI runner:

```bash
export RELEASE_KEYSTORE_PATH="/path/to/release.keystore"
export RELEASE_KEYSTORE_PASSWORD="your-strong-keystore-password"
export RELEASE_KEY_ALIAS="remotedevice"
export RELEASE_KEY_PASSWORD="your-strong-key-password"
```

These correspond directly to the signing configuration defined in `android/app/build.gradle.kts`:

```kotlin
signingConfigs {
    create("release") {
        storeFile = file(System.getenv("RELEASE_KEYSTORE_PATH") ?: "release.keystore")
        storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD") ?: "dummy_store_pass"
        keyAlias = System.getenv("RELEASE_KEY_ALIAS") ?: "remotedevice"
        keyPassword = System.getenv("RELEASE_KEY_PASSWORD") ?: "dummy_key_pass"
    }
}
```

---

## 3. Building Release Artifacts

Navigate to the `android/` directory:

### 3.1 Build Release APK (Direct Device Installation)
```bash
./gradlew assembleRelease
```
Output artifact: `android/app/build/outputs/apk/release/app-release.apk`

### 3.2 Build Release AAB (Google Play Distribution)
```bash
./gradlew bundleRelease
```
Output artifact: `android/app/build/outputs/bundle/release/app-release.aab`

---

## 4. ProGuard / R8 Obfuscation Verification

The release build automatically executes R8 minification and resource shrinking:
- `isMinifyEnabled = true`: Obfuscates classes, methods, and variables to prevent decompilation.
- `isShrinkResources = true`: Strips unused drawable vectors, layouts, and assets.
- `proguard-rules.pro`: Protects WebRTC JNI native symbols (`org.webrtc.**`), Jetpack Compose runtime modifiers, AndroidX Security Crypto primitives, and Background / Accessibility service entry points.

To verify the R8 mapping and obfuscation:
```bash
# Mapping file generated at:
cat android/app/build/outputs/mapping/release/mapping.txt | head -n 50
```

---

## 5. Device Installation & Runtime Setup

### 5.1 Sideloading onto Android Smartphone
```bash
# Install signed release APK via ADB
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

### 5.2 Mandatory One-Time Permissions Setup
For the application to function correctly as a remote control platform:

1. **Accessibility Service Activation**:
   - Navigate on phone: `Settings` -> `Accessibility` -> `Downloaded Apps` -> Select **"Remote Device Control"**.
   - Toggle **ON** and accept the system warning dialog.
   *(This enables remote tap, swipe, scroll, text entry, and system navigation).*

2. **Battery Optimization Exemption**:
   - Navigate: `Settings` -> `Apps` -> `Remote Device Control` -> `Battery` -> Select **"Unrestricted"**.
   *(Prevents Android Doze mode from killing the WebRTC video streaming foreground service).*

3. **Notification Permission (Android 13+)**:
   - Grant notification permission on app launch so the persistent foreground notification remains visible during active screen-sharing sessions.

4. **MediaProjection Consent**:
   - When remote viewing begins, the phone will display the standard Android OS dialog: *"Start recording or casting with Remote Device Control?"*.
   - Tap **"Start now"** to grant temporary capture consent.
