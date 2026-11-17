# Mobile MIR: Secure Android Remote Control Platform

[![Android CI/CD](https://github.com/JerowinRicky/mobile_mir/actions/workflows/android-ci.yml/badge.svg)](https://github.com/JerowinRicky/mobile_mir/actions/workflows/android-ci.yml)
[![WebRTC](https://img.shields.io/badge/WebRTC-DTLS--SRTP-brightgreen.svg)](https://webrtc.org/)
[![Django](https://img.shields.io/badge/Django-5.0%20%7C%20Channels-green.svg)](https://www.djangoproject.com/)
[![React](https://img.shields.io/badge/React-18%20%7C%20TypeScript-blue.svg)](https://react.dev/)
[![Docker](https://img.shields.io/badge/Docker-Compose%20Ready-blue.svg)](https://www.docker.com/)

A production-oriented, zero-trust remote control platform enabling secure screen viewing and interaction with Android smartphones directly from a modern desktop browser across any network.

---

## Architecture Overview

```
[ Android Smartphone ] ──(WebRTC Media: DTLS-SRTP)──> [ Desktop Web Browser ]
         │                                                      │
         └─────(WSS Signaling)─────> [ Django ASGI ] <──────────┘
                                           │
                                  [ PostgreSQL / Redis ]
```

1. **Android App**: Kotlin + Jetpack Compose with MediaProjection screen capture, hardware-accelerated H.264/VP8 WebRTC streaming, and AccessibilityService gesture simulation.
2. **Web Dashboard**: React 18 + TypeScript + Vite + Tailwind CSS with responsive canvas controls, WebRTC client, and real-time network quality HUD.
3. **Backend**: Django REST Framework + Django Channels + PostgreSQL + Redis with ephemeral PIN pairing, JWT rotation, and Coturn HMAC-SHA1 credential generator.
4. **Signaling & Relay**: WebSockets (WSS) and Coturn STUN/TURN server for NAT traversal.
5. **Gateway & Security**: Nginx reverse proxy with TLS 1.3/1.2, HSTS, and rate-limiting defense.

---

## Quick Start (Production Docker)

```bash
# 1. Clone repository
git clone https://github.com/JerowinRicky/mobile_mir.git
cd mobile_mir

# 2. Configure environment
cp .env.example .env
# Edit .env with your domain and secrets

# 3. Launch full stack
cd infra
docker compose --env-file ../.env up -d --build
```

---

## Android CI/CD

Automated builds and tests run on every push and pull request via GitHub Actions:
- **Workflow File**: `.github/workflows/android-ci.yml`
- **Artifacts**: Debug APK (`mobile-mir-debug-apk`) and Release APK automatically uploaded on build completion.

---

## Documentation

- [Final Architecture Specification](docs/final_architecture.md)
- [Security Audit Report](docs/security_audit_report.md)
- [Performance & Latency Test Report](docs/performance_test_report.md)
- [Production Deployment Guide](docs/deployment_guide.md)
- [Android Release Build & Signing Guide](docs/android_release_build.md)
- [Supported Action Matrix](docs/supported_action_matrix.md)
- [REST & WebSocket API Specifications](docs/api_spec.md)
