#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
APP_NAME="${APP_NAME:-hify}"
PID_FILE="${PID_FILE:-$ROOT_DIR/run/hify.pid}"
GRACEFUL_TIMEOUT="${GRACEFUL_TIMEOUT:-30}"
JAR_FILE="$ROOT_DIR/lib/hify-app.jar"

find_pid() {
  if [ -f "$PID_FILE" ]; then
    pid="$(cat "$PID_FILE" 2>/dev/null || true)"
    if [ -n "$pid" ] && kill -0 "$pid" >/dev/null 2>&1; then
      echo "$pid"
      return 0
    fi
    rm -f "$PID_FILE"
  fi

  pgrep -f "$JAR_FILE" 2>/dev/null | head -n 1 || true
}

pid="$(find_pid)"
if [ -z "$pid" ]; then
  echo "$APP_NAME is not running"
  exit 0
fi

echo "Stopping $APP_NAME, pid=$pid"
kill -TERM "$pid" >/dev/null 2>&1 || true

elapsed=0
while [ "$elapsed" -lt "$GRACEFUL_TIMEOUT" ]; do
  if ! kill -0 "$pid" >/dev/null 2>&1; then
    rm -f "$PID_FILE"
    echo "$APP_NAME stopped"
    exit 0
  fi
  sleep 1
  elapsed=$((elapsed + 1))
done

echo "$APP_NAME did not stop within ${GRACEFUL_TIMEOUT}s, killing pid=$pid" >&2
kill -KILL "$pid" >/dev/null 2>&1 || true
rm -f "$PID_FILE"
echo "$APP_NAME killed"
