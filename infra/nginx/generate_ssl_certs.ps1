# PowerShell script to generate self-signed TLS certificates for development/staging
$certDir = Join-Path $PSScriptRoot "ssl"
if (!(Test-Path $certDir)) {
    New-Item -ItemType Directory -Path $certDir -Force | Out-Null
}

$keyPath = Join-Path $certDir "server.key"
$crtPath = Join-Path $certDir "server.crt"

if ((Test-Path $keyPath) -and (Test-Path $crtPath)) {
    Write-Host "Certificates already exist in $certDir"
    exit 0
}

Write-Host "Generating self-signed certificate using OpenSSL..."
openssl req -x509 -nodes -days 365 -newkey rsa:4096 `
    -keyout $keyPath `
    -out $crtPath `
    -subj "/C=US/ST=California/L=SanFrancisco/O=RemoteControlPlatform/CN=localhost" `
    -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

Write-Host "Certificates generated successfully at $certDir"
