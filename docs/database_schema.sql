-- ============================================================================
-- SECURE ANDROID REMOTE CONTROL PLATFORM: POSTGRESQL SCHEMA SPECIFICATION
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ----------------------------------------------------------------------------
-- 1. USERS & AUTHENTICATION
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    email VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) DEFAULT '',
    last_name VARCHAR(100) DEFAULT '',
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_staff BOOLEAN NOT NULL DEFAULT FALSE,
    is_superuser BOOLEAN NOT NULL DEFAULT FALSE,
    is_mfa_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    mfa_secret VARCHAR(64) NULL,
    last_login TIMESTAMP WITH TIME ZONE NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);

-- ----------------------------------------------------------------------------
-- 2. DEVICES
-- ----------------------------------------------------------------------------
CREATE TYPE device_status_enum AS ENUM ('UNPAIRED', 'PENDING_APPROVAL', 'PAIRED', 'REVOKED');

CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    hardware_fingerprint VARCHAR(255) UNIQUE NOT NULL,
    device_name VARCHAR(100) NOT NULL,
    device_model VARCHAR(100) NOT NULL,
    android_version VARCHAR(20) NOT NULL,
    app_version VARCHAR(20) NOT NULL,
    public_key_pem TEXT NOT NULL,
    device_token_hash VARCHAR(255) NOT NULL,
    status device_status_enum NOT NULL DEFAULT 'UNPAIRED',
    last_seen_ip INET NULL,
    last_seen_at TIMESTAMP WITH TIME ZONE NULL,
    registered_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_devices_user_id ON devices(user_id);
CREATE INDEX idx_devices_hardware_fingerprint ON devices(hardware_fingerprint);
CREATE INDEX idx_devices_status ON devices(status);

-- ----------------------------------------------------------------------------
-- 3. PAIRING SESSIONS
-- ----------------------------------------------------------------------------
CREATE TYPE pairing_status_enum AS ENUM ('PENDING_VERIFICATION', 'AWAITING_APPROVAL', 'APPROVED', 'EXPIRED', 'FAILED');

CREATE TABLE pairing_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    target_user_id UUID NULL REFERENCES users(id) ON DELETE CASCADE,
    pin_salt VARCHAR(64) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    max_attempts INTEGER NOT NULL DEFAULT 3,
    status pairing_status_enum NOT NULL DEFAULT 'PENDING_VERIFICATION',
    client_ip INET NULL,
    client_user_agent TEXT NULL,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_pairing_device_id ON pairing_sessions(device_id);
CREATE INDEX idx_pairing_status ON pairing_sessions(status);
CREATE INDEX idx_pairing_expires_at ON pairing_sessions(expires_at);

-- ----------------------------------------------------------------------------
-- 4. ACTIVE & HISTORICAL WEBRTC SESSIONS
-- ----------------------------------------------------------------------------
CREATE TYPE session_status_enum AS ENUM ('INITIALIZING', 'CONNECTING', 'ACTIVE', 'TERMINATED', 'FAILED');

CREATE TABLE remote_sessions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    device_id UUID NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
    session_token VARCHAR(255) UNIQUE NOT NULL,
    status session_status_enum NOT NULL DEFAULT 'INITIALIZING',
    client_ip INET NULL,
    client_user_agent TEXT NULL,
    device_ip INET NULL,
    turn_used BOOLEAN NOT NULL DEFAULT FALSE,
    started_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP WITH TIME ZONE NULL,
    duration_seconds INTEGER NULL,
    termination_reason VARCHAR(100) NULL,
    bytes_sent BIGINT NOT NULL DEFAULT 0,
    bytes_received BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_remote_sessions_user_id ON remote_sessions(user_id);
CREATE INDEX idx_remote_sessions_device_id ON remote_sessions(device_id);
CREATE INDEX idx_remote_sessions_status ON remote_sessions(status);

-- ----------------------------------------------------------------------------
-- 5. AUDIT & SECURITY LOGS
-- ----------------------------------------------------------------------------
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID NULL REFERENCES users(id) ON DELETE SET NULL,
    device_id UUID NULL REFERENCES devices(id) ON DELETE SET NULL,
    event_type VARCHAR(100) NOT NULL, -- 'AUTH_LOGIN', 'DEVICE_REGISTER', 'PAIR_INIT', 'PAIR_VERIFY', 'PAIR_APPROVE', 'SESSION_START', 'SESSION_END', 'DEVICE_REVOKE'
    severity VARCHAR(20) NOT NULL DEFAULT 'INFO', -- 'INFO', 'WARN', 'CRITICAL'
    ip_address INET NULL,
    user_agent TEXT NULL,
    metadata JSONB NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_logs_event_type ON audit_logs(event_type);
CREATE INDEX idx_audit_logs_user_id ON audit_logs(user_id);
CREATE INDEX idx_audit_logs_device_id ON audit_logs(device_id);
CREATE INDEX idx_audit_logs_created_at ON audit_logs(created_at);
