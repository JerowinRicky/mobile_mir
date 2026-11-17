# Production Deployment Guide: Secure Android Remote Control Platform

**Target Environment**: Ubuntu 22.04 LTS / AWS EC2 (c6i.large or t3.xlarge recommended)  
**Architecture**: Docker Compose Orchestration (PostgreSQL 16, Redis 7.2, Daphne ASGI, Coturn TURN, Nginx Gateway)  
**Security Level**: High / Zero-Trust / Strict TLS 1.3  

---

## 1. System Requirements & Port Matrix

### 1.1 Compute & Storage Recommendations
- **CPU**: 2 vCPUs minimum (4 vCPUs recommended for multi-device concurrent streaming)
- **RAM**: 4 GB minimum (8 GB recommended)
- **Storage**: 40 GB NVMe / SSD
- **Operating System**: Ubuntu 22.04 LTS (x86_64 or aarch64)

### 1.2 Firewall & AWS Security Group Rules

| Port / Protocol | Direction | Source | Service / Purpose |
| :--- | :--- | :--- | :--- |
| **80 / TCP** | Inbound | `0.0.0.0/0` | HTTP (Certbot ACME challenge & HTTPS redirect) |
| **443 / TCP** | Inbound | `0.0.0.0/0` | HTTPS (Web UI, REST API, WSS WebSockets) |
| **3478 / UDP** | Inbound | `0.0.0.0/0` | STUN / TURN primary UDP listener |
| **3478 / TCP** | Inbound | `0.0.0.0/0` | STUN / TURN TCP fallback listener |
| **5349 / TCP** | Inbound | `0.0.0.0/0` | TURNS (TLS-wrapped TURN relay) |
| **49152-65535 / UDP**| Inbound | `0.0.0.0/0` | Coturn dynamic WebRTC media relay port range |
| **22 / TCP** | Inbound | Admin IP only | SSH management access |

---

## 2. Server Provisioning & Prerequisites

Run on fresh Ubuntu 22.04 instance:

```bash
# 1. Update system packages
sudo apt update && sudo apt upgrade -y

# 2. Install essential tools and Docker dependencies
sudo apt install -y curl git ufw openssl ca-certificates gnupg lsb-release

# 3. Install official Docker Engine and Docker Compose Plugin
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
sudo chmod a+r /etc/apt/keyrings/docker.gpg

echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

sudo apt update
sudo apt install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

# 4. Enable non-root docker execution
sudo usermod -aG docker $USER
```

---

## 3. Clone Repository & Setup Environment

```bash
# 1. Clone the repository
git clone https://github.com/your-org/secure-remote-android.git /opt/remotedevice
cd /opt/remotedevice

# 2. Copy production environment configuration
cp .env.example .env

# 3. Generate cryptographically strong random secrets
SECRET_DJANGO=$(openssl rand -hex 32)
SECRET_DB=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)
SECRET_REDIS=$(openssl rand -base64 24 | tr -dc 'a-zA-Z0-9' | head -c 24)
SECRET_COTURN=$(openssl rand -hex 24)
SECRET_JWT=$(openssl rand -hex 32)

# Update .env with generated secrets
sed -i "s|generate-a-strong-random-64-char-secret-key-here|$SECRET_DJANGO|g" .env
sed -i "s|generate-a-strong-postgres-password-here|$SECRET_DB|g" .env
sed -i "s|generate-a-strong-redis-password-here|$SECRET_REDIS|g" .env
sed -i "s|generate-a-strong-coturn-shared-secret-here|$SECRET_COTURN|g" .env
sed -i "s|generate-a-strong-jwt-signing-secret-here|$SECRET_JWT|g" .env

# Configure your public domain name
# e.g., YOUR_DOMAIN=remote.yourcompany.com
sed -i "s|yourdomain.com|remote.yourcompany.com|g" .env
```

---

## 4. Coturn TURN Server Configuration

Verify `/opt/remotedevice/infra/coturn/turnserver.conf`:

```conf
# /opt/remotedevice/infra/coturn/turnserver.conf
listening-port=3478
tls-listening-port=5349
realm=remote.yourcompany.com
server-name=turn.remote.yourcompany.com

# Ephemeral Time-Window REST API Authentication
use-auth-secret
static-auth-secret=<YOUR_COTURN_AUTH_SECRET>

# Security Hardening
fingerprint
lt-cred-mech
no-stdout-log
log-file=stdout
no-multicast-peers
no-cli

# WebRTC UDP Dynamic Relay Ports
min-port=49152
max-port=65535

# Inbound external IP assignment (replace with server public IPv4)
external-ip=<SERVER_PUBLIC_IPV4>
```

---

## 5. SSL/TLS Certificate Setup

### Option A: Let's Encrypt (Production with Public Domain)

```bash
sudo apt install -y certbot

# Obtain certificates via standalone ACME challenge
sudo certbot certonly --standalone -d remote.yourcompany.com -d turn.remote.yourcompany.com

# Link certificates into Nginx ssl volume
mkdir -p /opt/remotedevice/infra/nginx/ssl
sudo cp /etc/letsencrypt/live/remote.yourcompany.com/fullchain.pem /opt/remotedevice/infra/nginx/ssl/server.crt
sudo cp /etc/letsencrypt/live/remote.yourcompany.com/privkey.pem /opt/remotedevice/infra/nginx/ssl/server.key
sudo chmod 600 /opt/remotedevice/infra/nginx/ssl/server.key
sudo chmod 644 /opt/remotedevice/infra/nginx/ssl/server.crt
```

### Option B: Self-Signed Certificate (Staging / Development)

```bash
cd /opt/remotedevice/infra/nginx
bash generate_ssl_certs.sh
```

---

## 6. Frontend Build & Container Deployment

```bash
# 1. Build React frontend production bundle
cd /opt/remotedevice/frontend
npm ci
npm run build
# Bundle output generated in frontend/dist/

# 2. Launch production multi-container orchestration
cd /opt/remotedevice/infra
docker compose --env-file ../.env up -d --build

# 3. Apply database migrations inside backend container
docker compose exec backend python manage.py migrate

# 4. Create superuser for administrative dashboard
docker compose exec backend python manage.py createsuperuser

# 5. Verify all services are healthy
docker compose ps
```

Expected output:
```
NAME                    STATUS                 PORTS
remotedevice-coturn     Up (healthy)           Host network
remotedevice-postgres   Up (healthy)           5432/tcp
remotedevice-redis      Up (healthy)           6379/tcp
remotedevice-backend    Up (healthy)           8000/tcp
remotedevice-gateway    Up                     0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
```

---

## 7. Operational Runbooks

### 7.1 Database Backup & Restoration
```bash
# Automated database backup
docker compose exec postgres pg_dump -U remotedevice_user -d remotedevice_db | gzip > /opt/backups/db_$(date +%Y%m%d_%H%M%S).sql.gz

# Restore database from backup
gunzip -c /opt/backups/db_backup.sql.gz | docker compose exec -T postgres psql -U remotedevice_user -d remotedevice_db
```

### 7.2 Container Health Monitoring & Logs
```bash
# Tail live application logs across all containers
docker compose logs -f --tail=100

# View backend Daphne ASGI logs specifically
docker compose logs -f backend

# View Coturn media relay traffic
docker compose logs -f coturn
```

### 7.3 Zero-Downtime Rolling Update
```bash
# 1. Pull latest code
git pull origin main

# 2. Build updated frontend
cd frontend && npm run build && cd ../infra

# 3. Rebuild and restart backend container
docker compose up -d --no-deps --build backend
docker compose exec backend python manage.py migrate
docker compose restart gateway
```
