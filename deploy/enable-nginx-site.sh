#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEFAULT_CONF="$PROJECT_ROOT/deploy/rag-knowledge-nginx.conf"
CONF_SOURCE="${1:-$DEFAULT_CONF}"
SITE_NAME="${SITE_NAME:-rag-knowledge}"
SITE_AVAILABLE="/etc/nginx/sites-available/$SITE_NAME"
SITE_ENABLED="/etc/nginx/sites-enabled/$SITE_NAME"

if [[ $EUID -ne 0 ]]; then
  echo "[ERROR] Please run with sudo."
  exit 1
fi

if ! command -v nginx >/dev/null 2>&1; then
  apt update
  apt install -y nginx
fi

if [[ ! -f "$CONF_SOURCE" ]]; then
  echo "[ERROR] Nginx config not found: $CONF_SOURCE"
  echo "[INFO] Generate it first with deploy/generate-nginx-site.sh"
  exit 1
fi

cp "$CONF_SOURCE" "$SITE_AVAILABLE"
ln -sfn "$SITE_AVAILABLE" "$SITE_ENABLED"
nginx -t
systemctl enable nginx
systemctl restart nginx
systemctl --no-pager --full status nginx || true

echo "[OK] Nginx site enabled: $SITE_NAME"
echo "[INFO] Config: $SITE_AVAILABLE"
