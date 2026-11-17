# WebRTC Screen Mirroring & Cross-Network Transport Specification

## 1. Overview
The WebRTC Screen Mirroring pipeline streams real-time display frames from an Android device to a browser canvas with sub-100ms glass-to-glass latency across arbitrary network topologies. The architecture handles both direct peer-to-peer connectivity (over local Wi-Fi or open NATs) and relayed transport (across carrier-grade NATs and enterprise symmetric firewalls) via Coturn TURN relays.

---

## 2. End-to-End Media Pipeline

```
+----------------------------------------------------------------------------------------------------+
|                                      WEBRTC STREAMING PIPELINE                                     |
+----------------------------------------------------------------------------------------------------+

  [ Android Device ]
  ------------------
  Display Surface (1080x2400 @ 60 FPS)
         │
         ▼
  MediaProjectionManager.createScreenCaptureIntent() -> User Consent ("Start Now")
         │
         ▼
  ScreenCapturerAndroid (reads VirtualDisplay Surface)
         │
         ▼
  SurfaceTextureHelper (OpenGL ES GPU context on Dedicated Capture Thread)
         │
         ▼
  PeerConnectionFactory (DefaultVideoEncoderFactory: Hardware H.264 / VP8)
         │
         ▼
  VideoTrack ("ANTIGRAVITY_SCREEN_TRACK") -> PeerConnection.addTrack()
         │
         ▼
  DTLS 1.3 Handshake & SRTP Encryption (AES-GCM-128/256)
         │
         ├─────────────────────────────────────────┐
         ▼                                         ▼
  [ Direct P2P via STUN ]               [ Relayed via Coturn TURN ]
  (UDP Host / Srflx candidate)          (UDP/TCP Relay candidate: Port 3478/5349)
         │                                         │
         └────────────────────┬────────────────────┘
                              │
                              ▼
  [ Desktop Browser (React) ]
  ---------------------------
  RTCPeerConnection.ontrack -> MediaStream
                              │
                              ▼
  HTML5 <video autoPlay playsInline muted> Element
                              │
                              ▼
  Real-Time Remote Display + Pointer Coordinate Mapping
```

---

## 3. Video Encoding & Codec Negotiation

- **Primary Codec**: H.264 (Constrained Baseline Profile, Level 3.1 / 4.0).
  - Selected for universal hardware decoder acceleration across both Android and all desktop browsers (Chrome, Edge, Firefox, Safari).
- **Secondary / Fallback Codec**: VP8.
- **Resolution & Frame Rate**:
  - Captured at 1080p (or native device width scaled to max 1080px to preserve battery and GPU cycles).
  - Framerate target: 60 FPS under favorable network conditions; dynamically throttles to 30 FPS when RTT > 150ms.
- **Bitrate Adaptation**:
  - WebRTC Google Congestion Control (GCC) dynamically adjusts bitrate between 1.5 Mbps (poor cellular) to 8.0 Mbps (high-speed broadband/Wi-Fi).

---

## 4. NAT Traversal & Coturn Infrastructure

### 4.1 ICE Candidate Types
1. **Host Candidates**: Local IP addresses (e.g., `192.168.1.100:50000`). Used when both devices are on the same local network, providing 5–15ms latency.
2. **Server Reflexive (srflx) Candidates**: Public IP and port discovered via STUN (`stun.l.google.com:19302`). Used across Full Cone, Restricted Cone, and Port-Restricted Cone NATs.
3. **Relay Candidates**: Allocated by Coturn TURN server (`turn:turn.remotedevice.internal:3478`). Used when either device is behind a Symmetric NAT (typical of cellular 4G/5G connections or corporate firewalls).

### 4.2 Ephemeral Authentication Security
Coturn uses the time-limited shared secret mechanism:
$$\text{Username} = \text{timestamp} : \text{user\_id}$$
$$\text{Password} = \text{Base64}(\text{HMAC-SHA1}(\text{static\_secret}, \text{Username}))$$
Credentials expire in 3600 seconds, guaranteeing zero unauthorized bandwidth relay.

---

## 5. Latency and Connection Recovery

- **Sub-100ms Latency Optimizations**:
  - `tcpCandidatePolicy = DISABLED`: Forces UDP media relay, eliminating TCP head-of-line blocking.
  - `bundlePolicy = MAXBUNDLE`: Bundles all tracks over a single transport connection.
  - `rtcpMuxPolicy = REQUIRE`: Multiplexes RTP and RTCP packets over the same port pair.
  - Zero-buffer DataChannel (`maxRetransmits = 0`): Drops stale touch events if out-of-order, prioritizing live input responsiveness.
- **Connection Recovery (ICE Restart)**:
  - If network interface transitions (e.g., Wi-Fi drops to LTE), `pc.onconnectionstatechange` detects the `disconnected` state.
  - The browser automatically issues an ICE restart offer (`createOffer({ iceRestart: true })`), re-negotiating candidate pairs within 1.5 seconds without interrupting the user session.
