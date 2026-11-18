# Multi-stage Dockerfile for Django ASGI Backend
FROM python:3.11-slim as builder

WORKDIR /app

# Install system build dependencies for psycopg and cryptography
RUN apt-get update && apt-get install -y --no-install-recommends \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# Final Runtime Image
FROM python:3.11-slim

WORKDIR /app

# Install runtime dependencies for libpq
RUN apt-get update && apt-get install -y --no-install-recommends \
    libpq5 \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Copy installed wheels/packages from builder
COPY --from=builder /root/.local /root/.local
ENV PATH=/root/.local/bin:$PATH
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# Create non-root system user for security
RUN groupadd -r appgroup && useradd -r -g appgroup -d /app appuser

# Copy application source code
COPY . /app/
RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8008

# Health check endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -f http://localhost:8008/api/v1/auth/health/ || exit 1

# Launch ASGI server with Daphne for WebSockets and HTTP
CMD ["daphne", "-b", "0.0.0.0", "-p", "8008", "core.asgi:application"]
