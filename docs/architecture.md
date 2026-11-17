# System Architecture Specification

## 1. Executive Summary
The Secure Android Remote Control Platform provides real-time, low-latency screen mirroring and input control of an Android device from any modern web browser across arbitrary network environments. The system uses WebRTC for peer-to-peer audio/video streaming and bidirectional data communication, backed by Coturn STUN/TURN servers to traverse symmetric NATs and firewalls. Signaling, device lifecycle management, authentication, and pairing coordination are managed by a Django REST Framework + Django Channels backend, with PostgreSQL for persistent storage and Redis for high-speed ephemeral state and pub/sub signaling.

---

## 2. High-Level System Architecture

```
+----------------------------------------------------------------------------------------------------+
|                                      SYSTEM TOPOLOGY OVERVIEW                                      |
+----------------------------------------------------------------------------------------------------+

  +---------------------------+                                  +---------------------------+
  |      Android Device       |                                  |     Desktop Browser       |
  | (MediaProjection + A11y)  |                                  |   (React / TypeScript)    |
  +-------------+-------------+                                  +-------------+-------------+
                |                                                              |
     HTTPS /    |                                                   HTTPS /    |
     WSS        |                                                   WSS        |
                v                                                              v
      +---------+--------------------------------------------------------------+---------+
      |                           Nginx Reverse Proxy & SSL                              |
      +------------------------------------+---------------------------------------------+
                                           |
                                           v
      +------------------------------------+---------------------------------------------+
      |               Django REST Framework & Django Channels Backend                    |
      |  - accounts: JWT / MFA / Device Identity Verification                            |
      |  - pairing: Cryptographic Ephemeral PIN verification & approval state machine    |
      |  - signaling: WebSocket consumer routing SDP & ICE candidates                    |
      |  - security & audit: Rate limiting, tamper-evident audit logs                    |
      +---------------------+------------------------------------+-----------------------+
                            |                                    |
                            v                                    v
                +-----------+------------+           +-----------+------------+
                |   PostgreSQL Database  |           |   Redis Cache & Broker |
                | - Users & Devices      |           | - Channel Layer        |
                | - Sessions & Audit Logs|           | - Ephemeral PIN states |
                +------------------------+           | - Rate limit counters  |
                                                     +------------------------+

                +-------------------------------------------------------------+
                |            STUN / TURN Infrastructure (Coturn)              |
                | - Ephemeral credentials via HMAC-SHA1 REST API              |
                | - DTLS-SRTP Media Relay & Interactive Connectivity (ICE)    |
                +------------------------------+------------------------------+
                                               ^
                                               |
              +================================+================================+
              ||                                                               ||
  +-----------v---------------+                                  +-------------v-------------+
  |      Android Device       | <====== P2P WebRTC Connection =======> |     Desktop Browser       |
  |  - H.264/VP8 Screen Stream|    (Encrypted with DTLS 1.3 / SRTP)    |  - Remote Video Element   |
  |  - DataChannel Input Rcv  | <====== Low-Latency Input Data ======> |  - Pointer/Key Events     |
  +---------------------------+                                  +---------------------------+
```

---

## 3. Component Details & Responsibilities

### 3.1 Android Native Application
- **Language & Architecture**: Kotlin 1.9.22, Clean Architecture with MVI (Model-View-Intent) pattern, Coroutines & Flow.
- **UI Toolkit**: Jetpack Compose (BOM 2024.02.01).
- **Security & Keystore**: Hardware-backed EC (`secp256r1`) key generation inside the Android Keystore provider (`AndroidKeyStore`). Local credentials encrypted with AES-256-GCM via `EncryptedSharedPreferences`.
- **Screen Capture**: Android `MediaProjectionManager` creating a `VirtualDisplay` bound to WebRTC video source `SurfaceTextureHelper`.
- **Foreground Service**: Enforces a visible, non-dismissible notification (`MediaProjectionService`) with quick-action "Disconnect" button.
- **Remote Input**: `RemoteAccessibilityService` executing touch events (`dispatchGesture()`) and system navigation (`GLOBAL_ACTION_BACK`, `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`).
- **WebRTC Client**: `io.getstream:stream-webrtc-android:1.1.1` wrapping standard Google WebRTC C++ native binaries with hardware H.264/VP8 video encoding.

### 3.2 Backend & Signaling Server
- **Framework**: Python 3.11, Django 5.0.2, Django REST Framework 3.14.0.
- **ASGI & WebSockets**: Django Channels 4.0.0 with Daphne ASGI server.
- **Authentication**: JWT authentication with 15-minute access tokens and single-use refresh token rotation with blacklist.
- **Signaling Layer**: Channels consumer (`SignalingConsumer`) authenticating connections, managing room groups keyed by `session_id`, and exchanging SDP offers/answers and ICE candidates.
- **Coturn Integration**: Dynamic generation of temporary TURN authentication credentials using Coturn's REST API algorithm (`username = timestamp:user_id`, `password = Base64(HMAC-SHA1(secret, username))`).

### 3.3 Database & Cache Layer
- **PostgreSQL 16**: Relational storage for users, device records, active/historical sessions, and immutable audit logs.
- **Redis 7.2**:
  - Django Channels channel layer (`channels_redis`).
  - Ephemeral pairing PIN store: stores PBKDF2 hash of 6-digit PIN with a 300-second TTL.
  - Rate limiting sliding windows (e.g. 5 failed pairing attempts per IP per 10 minutes).

### 3.4 Web Client Dashboard
- **Framework**: React 18, TypeScript 5.3, Vite 5.1, Tailwind CSS 3.4.
- **WebRTC Consumer**: Standard browser `RTCPeerConnection` receiving H.264 video track and establishing an unordered, low-latency `RTCDataChannel` ("input-channel") for mouse and keyboard event transmission.
- **Aspect Ratio Normalization**: Normalizes click and touch coordinates to `[0.0, 1.0]` relative float vectors, decoupling browser viewport resolution from the physical Android screen density.

---

## 4. End-to-End Workflow

1. **Device Registration**:
   - The Android app generates a hardware-backed key pair.
   - It registers with the backend providing `device_name`, hardware fingerprint, and its public key.
2. **Device Pairing**:
   - The user opens the Android app and taps "Generate Pairing PIN".
   - The app uses `SecureRandom` to generate a 6-digit PIN.
   - The app hashes the PIN locally or securely transmits it to the backend over TLS. The backend stores `PBKDF2(PIN, salt)` in Redis with a 300-second TTL.
   - The web user logs into their account and inputs the PIN.
   - Backend validates the hash in Redis. If valid, an approval request is pushed to the phone.
   - The Android phone displays a native confirmation prompt: "Authorize Web Client from IP ...?".
   - Upon physical user tap "Approve", the device is permanently linked to the user's account.
3. **Session Initiation & Media Streaming**:
   - Web user clicks "Connect" on their registered device.
   - Signaling invites the Android client.
   - Android requests OS MediaProjection consent ("Start screen recording/casting").
   - User grants permission on the phone. The Foreground Service starts with an ongoing notification.
   - Android captures the screen, initializes WebRTC `PeerConnection`, and sends an SDP Offer via WebSocket.
   - Web client receives the SDP Offer, generates an SDP Answer, and ICE candidates are exchanged.
   - Direct P2P connection connects (or relays via Coturn TURN if NAT traversal fails).
   - Video is displayed in the browser with sub-100ms latency.
4. **Input Control**:
   - Web client captures pointer movements, taps, swipes, and keypresses.
   - Coordinates are normalized and dispatched over the WebRTC DataChannel.
   - The Android app receives the JSON payload and calls `RemoteAccessibilityService.dispatchGesture()`.
5. **Session Termination**:
   - Disconnection can be initiated immediately from either the browser "Disconnect" button or the Android notification quick action.
   - PeerConnection is closed, MediaProjection is stopped, and the Foreground Service terminates.
