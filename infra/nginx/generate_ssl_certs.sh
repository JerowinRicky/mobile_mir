#!/usr/bin/env bash
# Script to generate local self-signed TLS certificates for development/staging testing
set -e

CERT_DIR="$(dirname "$0")/ssl"
mkdir -p "$CERT_DIR"

if [ -f "$CERT_DIR/server.key" ] && [ -f "$CERT_DIR/server.crt" ]; then
    echo "SSL certificates already exist in $CERT_DIR"
    exit 0
fi

echo "Generating self-signed RSA-4096 SSL certificates in $CERT_DIR..."

openssl req -x509 -nodes -days 365 -newkey rsa:4096 \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -subj "/C=US/ST=California/L=SanFrancisco/O=RemoteControlPlatform/CN=localhost" \
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 "$CERT_DIR/server.key"
chmod 644 "$CERT_DIR/server.crt"

echo "Certificates generated successfully:"
echo " - Key:  $CERT_DIR/server.key"
echo " - Cert: $CERT_DIR/server.crt"
