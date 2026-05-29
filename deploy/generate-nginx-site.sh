#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_PATH="${1:-$PROJECT_ROOT/deploy/rag-knowledge-nginx.conf}"
SERVER_NAME="${SERVER_NAME:-_}"
FRONTEND_ROOT="${FRONTEND_ROOT:-$PROJECT_ROOT/frontend/dist}"
BACKEND_UPSTREAM="${BACKEND_UPSTREAM:-http://127.0.0.1:8081}"
CLIENT_MAX_BODY_SIZE="${CLIENT_MAX_BODY_SIZE:-200m}"

mkdir -p "$(dirname "$OUTPUT_PATH")"

cat > "$OUTPUT_PATH" <<EOF
server {
    listen 80;
    listen [::]:80;
    server_name $SERVER_NAME;

    client_max_body_size $CLIENT_MAX_BODY_SIZE;

    root $FRONTEND_ROOT;
    index index.html;

    location /api/ {
        proxy_pass $BACKEND_UPSTREAM;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_read_timeout 300s;
        proxy_send_timeout 300s;
    }

    location / {
        try_files \$uri \$uri/ /index.html;
    }
}
EOF

echo "[OK] Nginx config generated: $OUTPUT_PATH"
echo "[INFO] FRONTEND_ROOT=$FRONTEND_ROOT"
echo "[INFO] BACKEND_UPSTREAM=$BACKEND_UPSTREAM"
echo "[INFO] SERVER_NAME=$SERVER_NAME"
