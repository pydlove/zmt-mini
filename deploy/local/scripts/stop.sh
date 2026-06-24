#!/bin/bash

# 停止本地开发环境的四个服务
# admin-backend(8080), user-backend(8082), admin-frontend(5173), user-frontend(5174)

set -e

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info()  { echo -e "${BLUE}[INFO]${NC}  $1"; }
log_ok()    { echo -e "${GREEN}[OK]${NC}   $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

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
echo "       停止本地开发服务"
echo "========================================"
echo ""

kill_port 38080  "admin-backend"
kill_port 38082  "user-backend"
kill_port 35173  "admin-frontend"
kill_port 35174  "user-frontend"

echo ""
log_ok "本地服务已停止"
echo ""
