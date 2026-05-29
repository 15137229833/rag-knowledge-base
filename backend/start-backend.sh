#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR_PATH="$SCRIPT_DIR/target/rag-knowledge-backend-0.0.1-SNAPSHOT.jar"
CONFIG_PATH="$SCRIPT_DIR/llm-local.properties"

if [[ ! -f "$CONFIG_PATH" ]]; then
  echo "[ERROR] Config not found: $CONFIG_PATH"
  echo "Copy llm-local.properties.example to llm-local.properties and set your API key."
  exit 1
fi

if [[ ! -f "$JAR_PATH" ]]; then
  echo "[ERROR] Jar not found: $JAR_PATH"
  echo "Run: mvn -DskipTests package"
  exit 1
fi

echo "Starting backend with config: $CONFIG_PATH"
exec java -jar "$JAR_PATH" --spring.config.additional-location=file:"$CONFIG_PATH"
