# Security Architecture & Threat Model

## 1. Security Philosophy
The Secure Android Remote Control Platform follows the principles of **Zero Trust**, **Defense in Depth**, **Least Privilege**, and **Transparent User Consent**. Remote control capabilities in Android carry severe security implications; hence, the architecture is engineered to guarantee that unauthorized pairing, stealth streaming, or remote privilege escalation are impossible by design.

---

## 2. Threat Model & Countermeasures

| Threat | Attack Vector | Countermeasure |
| :--- | :--- | :--- |
| **PIN Brute-Forcing** | Attacker iterates 1,000,000 6-digit combinations on `/api/v1/pairing/verify/`. | 1. Maximum 3 failed attempts per PIN session before immediate invalidation.<br>2. IP-based rate limiting (5 attempts/15 min).<br>3. Strict 300-second TTL in Redis.<br>4. Mandatory secondary physical approval on the phone. |
| **Replay Attacks** | Eavesdropper captures signaling packets or REST API payloads and retransmits them. | 1. Nonce and timestamp validation with 30s clock skew tolerance.<br>2. Single-use refresh token rotation.<br>3. WSS and HTTPS strict transport security (HSTS). |
| **Eavesdropping / MITM** | Interception of video streams or remote keystrokes in transit. | 1. DTLS 1.2 / 1.3 encryption for WebRTC media (SRTP) and DataChannels (SCTP over DTLS).<br>2. Dynamic TURN credentials generated per session using HMAC-SHA1. |
| **Stolen Device Credentials** | Attacker extracts application data or storage on a compromised phone. | 1. Private keys stored inside hardware-backed Android Keystore (`KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT` or `SECURITY_LEVEL_STRONGBOX`).<br>2. Local tokens encrypted with AES-256-GCM via `EncryptedSharedPreferences`. |
| **Stealth Surveillance** | Malicious actor attempts hidden background screen capture without user knowledge. | 1. Persistent, non-dismissible Android Foreground Service notification (`IMPORTANCE_HIGH`).<br>2. Android OS native cast/mirroring indicator permanently visible in the status bar.<br>3. Direct "Disconnect" action button in notification. |
| **Unauthorized Remote Input (Tapjacking)** | Injected touch events attempting to grant system permissions or install APKs. | 1. Android 12+ prevents `AccessibilityService` gesture dispatch over system dialogs and permission prompts.<br>2. Keyguard / PIN screen touch injection blocked by Android OS sandbox.<br>3. Input rate limiting and validation on coordinates. |
| **Session Hijacking** | Stealing access tokens to control another user's device. | 1. Short-lived JWT access tokens (15 minutes).<br>2. Refresh tokens stored in `HttpOnly`, `SameSite=Strict` secure cookies.<br>3. Device ownership verification on every signaling channel join. |

---

## 3. Cryptographic Ephemeral PIN Implementation

1. **Generation on Device**:
   - The Android app invokes `java.security.SecureRandom`.
   - Generates an integer in the range `[100000, 999999]`.
   - Formatted for human readability as `XXX-XXX`.
2. **Server-Side Storage**:
   - The PIN is never saved in plaintext in PostgreSQL.
   - The backend computes:
     $$\text{hash} = \text{PBKDF2-HMAC-SHA256}(\text{PIN}, \text{salt}, \text{iterations}=100,000)$$
   - Stored in Redis as:
     `pairing:pin:<hash> -> {"device_id": UUID, "attempts": 0, "salt": HEX}` with `EX = 300`.
3. **Attempt & Lockout Logic**:
   - On submission from web dashboard:
     - Check Redis attempt counter. If $\ge 3$, delete key and emit `429 Too Many Requests / Locked Out`.
     - Compute hash with the stored salt using constant-time comparison (`hmac.compare_digest`).
     - If incorrect, increment attempt count in Redis.
     - If correct, delete PIN key from Redis and transition pairing session to `AWAITING_DEVICE_CONFIRMATION`.

---

## 4. Hardware-Backed Device Identity (Android Keystore)

The Android application creates an asymmetric EC key pair on initial launch:
- **Alias**: `antigravity_device_identity_key`
- **Algorithm**: `KeyProperties.KEY_ALGORITHM_EC`
- **Curve**: `secp256r1` (NIST P-256)
- **Purposes**: `PURPOSE_SIGN | PURPOSE_VERIFY`
- **Digest**: `DIGEST_SHA256`
- **Hardware Backing**: Stored in TEE (Trusted Execution Environment) or StrongBox Keymaster. The private key never leaves secure hardware.

When pairing or authenticating, the device signs a backend-provided challenge `nonce + timestamp`. The backend verifies the signature against the registered public key, preventing device spoofing.

---

## 5. WebRTC Transport Security

- **DTLS-SRTP**: WebRTC enforces end-to-end encryption of all media packets (RTP) and data packets (SCTP) using Datagram Transport Layer Security (DTLS).
- **Cipher Suites**: `TLS_ECDHE_ECDSA_WITH_AES_128_GCM_SHA256`, `TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256`.
- **Coturn Ephemeral Credentials**:
  ```
  username = timestamp + ":" + user_id
  password = base64(hmac_sha1(turn_static_auth_secret, username))
  ```
  Credentials expire in 3600 seconds, preventing unauthorized use of TURN relay bandwidth.

---

## 6. Android Compatibility & Platform Guardrails

Android OS enforces stringent privacy and security restrictions. The platform operates strictly within documented, approved Android APIs:

1. **MediaProjection User Consent**:
   - Per Android 14 (API 34) guidelines, `createScreenCaptureIntent()` requires explicit user interaction each time a projection session begins.
   - Foreground service must declare `android:foregroundServiceType="mediaProjection"`.
2. **FLAG_SECURE Windows**:
   - Any window with `WindowManager.LayoutParams.FLAG_SECURE` (such as banking applications, password managers, or Chrome Incognito) is rendered completely black by the Android Compositor. This is an intentional Android OS security feature and will not be bypassed.
3. **AccessibilityService Gesture Security**:
   - `dispatchGesture()` cannot interact with lock screens (Keyguard) or system-level permission dialogs.
   - This ensures the remote user cannot grant themselves additional permissions remotely.
