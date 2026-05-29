#!/usr/bin/env bash
set -euo pipefail

APP_DIR="$(cd "$(dirname "$0")/.." && pwd)/backend"
JAR_PATH="$APP_DIR/target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar"
CONFIG_PATH="$APP_DIR/llm-local.properties"
ENV_PATH="$APP_DIR/backend.env"
LOG_PATH="$APP_DIR/backend.out"
PID_PATH="$APP_DIR/backend.pid"

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

set -a
source "$ENV_PATH"
set +a

if [[ -z "${SPRING_AI_OPENAI_API_KEY:-}" || "$SPRING_AI_OPENAI_API_KEY" == "replace-with-your-qwen-key" ]]; then
  echo "[ERROR] Please set SPRING_AI_OPENAI_API_KEY in $ENV_PATH"
  exit 1
fi

nohup java -jar "$JAR_PATH" --spring.config.additional-location=file:"$CONFIG_PATH" > "$LOG_PATH" 2>&1 &
echo $! > "$PID_PATH"

echo "[OK] Backend started. PID=$(cat "$PID_PATH")"
echo "[INFO] Log file: $LOG_PATH"
