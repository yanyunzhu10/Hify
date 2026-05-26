#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn()  { echo -e "${YELLOW}[WARN]${NC}  $*"; }
error() { echo -e "${RED}[ERROR]${NC} $*" >&2; exit 1; }

GRACEFUL_TIMEOUT=15

# stop_process <label> <pidfile> <pgrep-pattern>
#   1. 优先读 PID 文件
#   2. PID 文件不存在或 PID 已失效，fallback 到 pgrep 按进程特征查找
#   3. SIGTERM → 等待 → SIGKILL
stop_process() {
  local svc="$1"
  local pf="$2"
  local pattern="$3"
  local p=""

  # ── 1. 从 PID 文件获取 PID ──────────────────────────────────────────
  if [ -f "$pf" ]; then
    p=$(cat "$pf")
    if ! kill -0 "$p" 2>/dev/null; then
      warn "${svc}: PID 文件中的进程 ($p) 已不存在，尝试 pgrep 兜底"
      p=""
    fi
  fi

  # ── 2. Fallback: 按进程特征查找 ─────────────────────────────────────
  if [ -z "$p" ]; then
    p=$(pgrep -f "$pattern" 2>/dev/null | head -1 || true)
    if [ -n "$p" ]; then
      warn "${svc}: 通过进程特征 '$pattern' 找到 PID $p"
    else
      warn "${svc}: 未找到运行中的进程，跳过"
      rm -f "$pf"
      return
    fi
  fi

  # ── 3. SIGTERM → 等待 → SIGKILL ─────────────────────────────────────
  info "${svc}: 发送 SIGTERM (PID $p)..."
  kill -TERM "$p"

  local waited=0
  while kill -0 "$p" 2>/dev/null; do
    if [ "$waited" -ge "$GRACEFUL_TIMEOUT" ]; then
      warn "${svc}: ${GRACEFUL_TIMEOUT}s 内未退出，发送 SIGKILL (PID $p)"
      kill -KILL "$p" 2>/dev/null || true
      break
    fi
    sleep 1
    waited=$((waited + 1))
  done

  rm -f "$pf"
  info "${svc}: 已停止"
}

# 前端先停（无状态），再停后端（Spring Boot 有 graceful shutdown）
stop_process "前端 (Vite)"         "$ROOT_DIR/logs/hify-web.pid" "vite"
stop_process "后端 (Spring Boot)"  "$ROOT_DIR/logs/hify-app.pid" "hify-app"

info "所有服务已停止"
