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

BACKEND_PID_FILE="$ROOT_DIR/logs/hify-app.pid"
FRONTEND_PID_FILE="$ROOT_DIR/logs/hify-web.pid"

cleanup() {
  # 启动失败时清理还未写入 PID 文件的后台进程
  local pid
  if [ -f "$BACKEND_PID_FILE" ]; then
    pid=$(cat "$BACKEND_PID_FILE")
    kill -0 "$pid" 2>/dev/null && kill "$pid" && warn "已终止后端（PID: ${pid}）"
  fi
  if [ -f "$FRONTEND_PID_FILE" ]; then
    pid=$(cat "$FRONTEND_PID_FILE")
    kill -0 "$pid" 2>/dev/null && kill "$pid" && warn "已终止前端（PID: ${pid}）"
  fi
}
trap cleanup ERR

# ── 1. 检查 MySQL ────────────────────────────────────────────────────
info "检查 MySQL（localhost:3306）..."
nc -z localhost 3306 2>/dev/null \
  || error "MySQL 不可用，请确认 MySQL 已启动并监听 localhost:3306"
info "MySQL 可用"

# ── 2. 检查 Redis ────────────────────────────────────────────────────
info "检查 Redis（localhost:6379）..."
nc -z localhost 6379 2>/dev/null \
  || error "Redis 不可用，请确认 Redis 已启动并监听 localhost:6379"
info "Redis 可用"

# ── 3. 构建后端 ──────────────────────────────────────────────────────
info "构建后端（mvn clean package -DskipTests）..."
cd "$ROOT_DIR"
./mvnw clean package -DskipTests -q \
  || error "后端构建失败，请检查 Maven 输出"
info "后端构建成功"

# ── 4. 启动后端 ──────────────────────────────────────────────────────
JAR=$(ls "$ROOT_DIR"/hify-app/target/hify-app-*.jar 2>/dev/null | head -1)
[ -z "$JAR" ] && error "未找到 hify-app jar 包，请检查构建输出"

info "启动后端：$JAR"
java -jar "$JAR" > "$ROOT_DIR/logs/backend.log" 2>&1 &
echo $! > "$BACKEND_PID_FILE"
info "后端已启动（PID: $(cat "$BACKEND_PID_FILE")），日志：logs/backend.log"

# ── 5. 等待后端健康检查 ──────────────────────────────────────────────
info "等待后端就绪..."
MAX_WAIT=60
waited=0
until curl -sf http://localhost:8080/api/v1/health > /dev/null 2>&1; do
  if ! kill -0 "$(cat "$BACKEND_PID_FILE")" 2>/dev/null; then
    error "后端进程意外退出，查看日志：logs/backend.log"
  fi
  if [ "$waited" -ge "$MAX_WAIT" ]; then
    error "后端 ${MAX_WAIT}s 内未就绪，查看日志：logs/backend.log"
  fi
  sleep 2
  waited=$((waited + 2))
  echo -ne "\r  已等待 ${waited}s / ${MAX_WAIT}s ..."
done
echo ""
info "后端健康检查通过"

# ── 6. 启动前端 ──────────────────────────────────────────────────────
info "启动前端开发服务器..."
cd "$ROOT_DIR/hify-web"
./node_modules/.bin/vite > "$ROOT_DIR/logs/frontend.log" 2>&1 &
echo $! > "$FRONTEND_PID_FILE"
info "前端已启动（PID: $(cat "$FRONTEND_PID_FILE")），日志：logs/frontend.log"
info "所有服务已就绪，使用 ./stop.sh 停止"
