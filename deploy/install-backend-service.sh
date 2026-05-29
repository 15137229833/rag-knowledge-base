#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)/backend"
SERVICE_NAME="rag-knowledge-backend"
UNIT_PATH="/etc/systemd/system/${SERVICE_NAME}.service"
RUN_USER="${SUDO_USER:-$(whoami)}"
JAR_PATH="$APP_DIR/target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar"
CONFIG_PATH="$APP_DIR/llm-local.properties"
ENV_PATH="$APP_DIR/backend.env"

if [[ $EUID -ne 0 ]]; then
  echo "[ERROR] Please run with sudo."
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "[ERROR] Jar not found: $JAR_PATH"
  exit 1
fi

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "[ERROR] Config not found: $CONFIG_PATH"
  exit 1
fi

if [[ ! -f "$ENV_PATH" ]]; then
  cat > "$ENV_PATH" <<'EOF'
SPRING_AI_OPENAI_API_KEY=replace-with-your-qwen-key
EOF
  chmod 600 "$ENV_PATH"
  echo "[INFO] Created $ENV_PATH . Please fill in your real key and rerun this script."
  exit 1
fi

cat > "$UNIT_PATH" <<EOF
[Unit]
Description=RAG Knowledge Backend
After=network.target

[Service]
Type=simple
User=$RUN_USER
WorkingDirectory=$APP_DIR
EnvironmentFile=$ENV_PATH
ExecStart=/usr/bin/java -jar $JAR_PATH --spring.config.additional-location=file:$CONFIG_PATH
Restart=always
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
EOF

systemctl daemon-reload
systemctl enable "$SERVICE_NAME"
systemctl restart "$SERVICE_NAME"
systemctl --no-pager --full status "$SERVICE_NAME" || true

echo "[OK] systemd service installed: $SERVICE_NAME"
echo "[INFO] Logs: journalctl -u $SERVICE_NAME -f"
