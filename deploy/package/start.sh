#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck disable=SC1090
  . "$ENV_FILE"
  set +a
fi

APP_NAME="${APP_NAME:-hify}"
SERVER_PORT="${SERVER_PORT:-8080}"
JAVA_BIN="${JAVA_BIN:-java}"
JAVA_OPTS="${JAVA_OPTS:-}"
SPRING_PROFILES_ACTIVE="${SPRING_PROFILES_ACTIVE:-prod}"
CONFIG_FILE="${CONFIG_FILE:-$ROOT_DIR/config/application.yml}"
PID_FILE="${PID_FILE:-$ROOT_DIR/run/hify.pid}"
LOG_FILE="${LOG_FILE:-$ROOT_DIR/logs/hify-console.log}"
STARTUP_TIMEOUT="${STARTUP_TIMEOUT:-60}"
HEALTH_URL="${HEALTH_URL:-http://127.0.0.1:${SERVER_PORT}/api/v1/health}"
JAR_FILE="$ROOT_DIR/lib/hify-app.jar"
FRONTEND_INDEX="$ROOT_DIR/frontend/index.html"

mkdir -p "$ROOT_DIR/logs" "$ROOT_DIR/run" "$ROOT_DIR/upload"

if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
  echo "ERROR: Java not found: $JAVA_BIN" >&2
  exit 1
fi

if [ ! -f "$JAR_FILE" ]; then
  echo "ERROR: backend jar not found: $JAR_FILE" >&2
  exit 1
fi

if [ ! -f "$FRONTEND_INDEX" ]; then
  echo "ERROR: frontend dist not found: $FRONTEND_INDEX" >&2
  exit 1
fi

if [ ! -f "$CONFIG_FILE" ]; then
  echo "ERROR: config file not found: $CONFIG_FILE" >&2
  exit 1
fi

if [ -f "$PID_FILE" ]; then
  old_pid="$(cat "$PID_FILE" 2>/dev/null || true)"
  if [ -n "$old_pid" ] && kill -0 "$old_pid" >/dev/null 2>&1; then
    echo "$APP_NAME is already running, pid=$old_pid" >&2
    exit 1
  fi
  rm -f "$PID_FILE"
fi

cd "$ROOT_DIR"

# shellcheck disable=SC2086
nohup "$JAVA_BIN" $JAVA_OPTS \
  -DLOG_DIR="$ROOT_DIR/logs" \
  -jar "$JAR_FILE" \
  --spring.profiles.active="$SPRING_PROFILES_ACTIVE" \
  --spring.config.additional-location="file:$CONFIG_FILE" \
  --server.port="$SERVER_PORT" \
  >> "$LOG_FILE" 2>&1 &

pid=$!
echo "$pid" > "$PID_FILE"

echo "$APP_NAME starting, pid=$pid"
echo "log: $LOG_FILE"
echo "health: $HEALTH_URL"

elapsed=0
while [ "$elapsed" -lt "$STARTUP_TIMEOUT" ]; do
  if ! kill -0 "$pid" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    echo "ERROR: $APP_NAME exited during startup. See log: $LOG_FILE" >&2
    tail -n 80 "$LOG_FILE" >&2 || true
    exit 1
  fi

  if curl -fsS "$HEALTH_URL" >/dev/null 2>&1; then
    echo "$APP_NAME started successfully"
    echo "pid: $PID_FILE"
    echo "url: http://127.0.0.1:$SERVER_PORT/"
    echo "stop: $ROOT_DIR/bin/stop.sh"
    exit 0
  fi

  sleep 1
  elapsed=$((elapsed + 1))
done

echo "ERROR: $APP_NAME did not become healthy within ${STARTUP_TIMEOUT}s" >&2
echo "process is still running, pid=$pid; see log: $LOG_FILE" >&2
exit 1
