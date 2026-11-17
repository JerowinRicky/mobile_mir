# REST API & WebSocket Signaling Specification

## Base URL: `https://api.remotedevice.internal/api/v1`

---

## 1. Authentication Endpoints

### 1.1 User Login
- **Endpoint**: `POST /auth/login/`
- **Request**:
  ```json
  {
    "email": "user@example.com",
    "password": "SecurePassword123!"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "access_token": "eyJhbGciOi...",
    "expires_in": 900,
    "user": {
      "id": "e6f42c22-b52b-426c-8973-10702d76378e",
      "email": "user@example.com"
    }
  }
  ```

---

## 2. Device Management Endpoints

### 2.1 Register Device
- **Endpoint**: `POST /devices/register/`
- **Request**:
  ```json
  {
    "hardware_fingerprint": "a1b2c3d4-e5f6-7890",
    "device_name": "Pixel 8 Pro",
    "device_model": "Google Pixel 8 Pro",
    "android_version": "14.0",
    "app_version": "1.0.0",
    "public_key_pem": "-----BEGIN PUBLIC KEY-----\n..."
  }
  ```
- **Response (201 Created)**:
  ```json
  {
    "device_id": "93bfa61e-d4c8-472e-83ff-a7bd56db5691",
    "device_token": "dev_sec_99a8b7c6...",
    "status": "UNPAIRED"
  }
  ```

### 2.2 List Registered Devices
- **Endpoint**: `GET /devices/` (Bearer Auth)
- **Response (200 OK)**:
  ```json
  [
    {
      "id": "93bfa61e-d4c8-472e-83ff-a7bd56db5691",
      "device_name": "Pixel 8 Pro",
      "device_model": "Google Pixel 8 Pro",
      "status": "PAIRED",
      "last_seen_at": "2026-11-17T06:15:00Z"
    }
  ]
  ```

---

## 3. Ephemeral PIN Pairing Endpoints

### 3.1 Initiate Pairing (Android Device)
- **Endpoint**: `POST /pairing/initiate/` (Device Token Auth)
- **Request**:
  ```json
  {
    "device_id": "93bfa61e-d4c8-472e-83ff-a7bd56db5691",
    "pin_hash": "c2b6f1208945628b0304a08e64c398324e...",
    "pin_salt": "9f7a6b2c...",
    "expires_in": 300
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "pairing_session_id": "f5127ba2-...",
    "status": "PENDING_VERIFICATION",
    "expires_at": "2026-11-17T06:20:00Z"
  }
  ```

### 3.2 Verify Pairing PIN (Web Client)
- **Endpoint**: `POST /pairing/verify/` (Bearer Auth)
- **Request**:
  ```json
  {
    "pin": "849201"
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "pairing_session_id": "f5127ba2-...",
    "device_name": "Pixel 8 Pro",
    "status": "AWAITING_DEVICE_APPROVAL",
    "message": "PIN verified. Confirm connection request on your Android device."
  }
  ```

### 3.3 Approve Pairing (Android Device)
- **Endpoint**: `POST /pairing/approve/` (Device Token Auth)
- **Request**:
  ```json
  {
    "pairing_session_id": "f5127ba2-...",
    "approved": true,
    "signed_challenge": "30450221008d..."
  }
  ```
- **Response (200 OK)**:
  ```json
  {
    "status": "PAIRED",
    "user_email": "user@example.com"
  }
  ```

---

## 4. WebSocket Signaling Channel Specification

- **Endpoint**: `wss://api.remotedevice.internal/ws/signaling/?session_id=<UUID>&token=<JWT>`

### Message Types

#### 1. SDP Offer (Android -> Web)
```json
{
  "type": "sdp_offer",
  "sdp": "v=0\r\no=- 4611731400 2 IN IP4 127.0.0.1..."
}
```

#### 2. SDP Answer (Web -> Android)
```json
{
  "type": "sdp_answer",
  "sdp": "v=0\r\no=- 5822910291 2 IN IP4 127.0.0.1..."
}
```

#### 3. ICE Candidate (Bidirectional)
```json
{
  "type": "ice_candidate",
  "candidate": {
    "candidate": "candidate:1 1 UDP 2122252543 192.168.1.100 50000 typ host...",
    "sdpMid": "0",
    "sdpMLineIndex": 0
  }
}
```

#### 4. Remote Input Command (Web -> Android via WebRTC DataChannel)
```json
{
  "action": "TAP",
  "x": 0.452,
  "y": 0.781,
  "duration_ms": 50
}
```
```json
{
  "action": "SWIPE",
  "start_x": 0.5,
  "start_y": 0.8,
  "end_x": 0.5,
  "end_y": 0.2,
  "duration_ms": 300
}
```
```json
{
  "action": "GLOBAL_NAV",
  "target": "BACK" // "BACK", "HOME", "RECENTS"
}
```
```json
{
  "action": "INPUT_TEXT",
  "text": "Hello remote world!"
}
```
