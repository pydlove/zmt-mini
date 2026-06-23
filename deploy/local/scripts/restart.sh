#!/bin/bash

# 一键重启四个服务
# admin-backend(8080), user-backend(8082), admin(5173), user-frontend(5174)

set -e

BASE_DIR="$(cd "$(dirname "$0")/../../.." && pwd)"

# Jasypt 加密密钥（生产环境务必修改）
export JASYPT_ENCRYPTOR_PASSWORD=MySecretKey2024

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}   $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

# 加载 deploy/.env（如果存在）
ENV_FILE="$BASE_DIR/deploy/remote/.env"
if [ -f "$ENV_FILE" ]; then
  set -a
  source "$ENV_FILE"
  set +a
  log_info "已加载环境变量: $ENV_FILE"
fi

# 线上 API 地址（本地环境流程管理走线上数据）
# 请确保 deploy/remote/.env 中设置了 ONLINE_API_BASE_URL，例如：
# ONLINE_API_BASE_URL=https://your-domain.com
if [ -z "${ONLINE_API_BASE_URL:-}" ]; then
  log_warn "未设置 ONLINE_API_BASE_URL，流程管理将使用本地数据"
  log_warn "如需同步线上状态，请在 deploy/remote/.env 中添加: ONLINE_API_BASE_URL=https://your-domain.com"
fi

# 本地环境禁用流程调度器（避免和线上重复执行）
export PROCESS_AUTO_SCHEDULER_ENABLED=false
log_info "本地调度器已禁用 (PROCESS_AUTO_SCHEDULER_ENABLED=false)"

# 根据端口停止进程
kill_port() {
  local port=$1
  local pname=$2
  local pids
  pids=$(lsof -ti :"$port" 2>/dev/null || true)
  if [ -n "$pids" ]; then
    echo "$pids" | xargs kill -9 2>/dev/null || true
    log_warn "已停止 $pname (端口 $port)"
  else
    log_info "$pname (端口 $port) 未运行"
  fi
}

echo "========================================"
echo "       一键重启四个服务"
echo "========================================"
echo ""

# 1. 停止旧进程
log_info "步骤 1/4: 停止旧进程..."
kill_port 38080  "admin-backend"
kill_port 38082  "user-backend"
kill_port 35173  "admin-frontend"
kill_port 35174  "user-frontend"
sleep 1
log_ok "旧进程已清理"
echo ""

# 2. 编译后端
log_info "步骤 2/4: 编译后端服务..."

log_info "编译 admin-backend ..."
cd "$BASE_DIR/services/admin-backend"
mvn compile -q
log_ok "admin-backend 编译完成"

log_info "编译 user-backend ..."
cd "$BASE_DIR/services/user-backend"
mvn compile -q
log_ok "user-backend 编译完成"
echo ""

# 3. 启动后端
log_info "步骤 3/4: 启动后端服务..."

mkdir -p "$BASE_DIR/logs"

cd "$BASE_DIR/services/admin-backend"
nohup mvn spring-boot:run -q -Dspring-boot.run.jvmArguments="-Dspring.profiles.active=local" > "$BASE_DIR/logs/admin-backend.log" 2>&1 &
log_ok "admin-backend 已启动 (端口 8080, PID $!, profile=local)"

cd "$BASE_DIR/services/user-backend"
nohup mvn spring-boot:run -q > "$BASE_DIR/logs/user-backend.log" 2>&1 &
log_ok "user-backend 已启动 (端口 8082, PID $!)"

# 等待后端就绪
log_info "等待后端服务就绪 (约 15 秒)..."
sleep 15
echo ""

# 4. 启动前端
log_info "步骤 4/4: 启动前端服务..."

cd "$BASE_DIR/services/admin-frontend"
nohup npm run dev -- --host > "$BASE_DIR/logs/admin-frontend.log" 2>&1 &
log_ok "admin-frontend 已启动 (端口 5173, PID $!)"

cd "$BASE_DIR/services/user-frontend"
nohup npm run dev -- --host > "$BASE_DIR/logs/user-frontend.log" 2>&1 &
log_ok "user-frontend 已启动 (端口 5174, PID $!)"

echo ""
echo "========================================"
echo -e "           ${GREEN}全部启动完成${NC}"
echo "========================================"
echo ""
echo "服务地址:"
echo "  管理后台:  http://localhost:5173"
echo "  用户端:    http://localhost:5174"
echo "  Admin API: http://localhost:8080"
echo "  User API:  http://localhost:8082"
echo ""
echo "日志目录: $BASE_DIR/logs/"
echo ""
echo "查看实时日志:"
echo "  tail -f $BASE_DIR/logs/admin-backend.log"
echo "  tail -f $BASE_DIR/logs/user-backend.log"
echo ""
