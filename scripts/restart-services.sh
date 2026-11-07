#!/usr/bin/env bash
# qMusic 后台服务一键重启（多音源 + 网易云上游 + 网关 + Cloudflare 隧道）
#
# 用法:
#   bash scripts/restart-services.sh              # 全套（含隧道 + 自动更新并推送线上地址）
#   bash scripts/restart-services.sh --no-tunnel  # 只重启本地三个进程
#   bash scripts/restart-services.sh --no-push    # 起隧道但不改 server-address.json
#
# 说明：本脚本自身若由 WMI/计划任务等「非会话」进程拉起，其子进程可脱离 WorkBuddy
#       的 job object，父脚本退出后继续存活。会话内直接跑则只活到会话结束。

set -uo pipefail

ROOT="/f/project/AiCoding/deepseek/qMusic"
WIN_ROOT="F:/project/AiCoding/deepseek/qMusic"
LOG_DIR="$ROOT/.workbuddy/logs"
mkdir -p "$LOG_DIR"
STAMP="$(date +%Y%m%d-%H%M%S)"
RUN_LOG="$LOG_DIR/restart-$STAMP.log"

USE_TUNNEL=1
USE_PUSH=1
for a in "$@"; do
  case "$a" in
    --no-tunnel) USE_TUNNEL=0 ;;
    --no-push)   USE_PUSH=0 ;;
  esac
done

log() { echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" >> "$RUN_LOG"; echo "$*"; }

echo "=== qMusic 服务重启 $(date '+%Y-%m-%d %H:%M:%S') ==="

listening() {
  MSYS_NO_PATHCONV=1 netstat -ano 2>/dev/null | grep LISTENING | grep -qE ":$1[[:space:]]"
}

stop_port() {
  local port=$1 pids p
  pids=$(MSYS_NO_PATHCONV=1 netstat -ano 2>/dev/null | grep LISTENING \
    | grep -E ":${port}[[:space:]]" | awk '{print $NF}' | sort -u)
  for p in $pids; do
    [ -z "$p" ] && continue
    MSYS_NO_PATHCONV=1 taskkill /PID "$p" /F >/dev/null 2>&1 && log "停止 :$port (pid $p)"
  done
}

# ---------- 1. 停旧 ----------
for p in 3300 3301 3100; do stop_port "$p"; done
MSYS_NO_PATHCONV=1 taskkill /IM cloudflared.exe /F >/dev/null 2>&1 && log "停止 cloudflared"
sleep 2

# ---------- 2. 起三个本地服务 ----------
NODE_BIN="$(which node)"
[ -z "$NODE_BIN" ] && { log "!! 找不到 node"; exit 1; }

if [ -x "$ROOT/server/musiclib-service/qmusic-musiclib.exe" ]; then
  cd "$ROOT/server/musiclib-service" || exit 1
  MUSICLIB_CACHE="$WIN_ROOT/server/.cache" nohup ./qmusic-musiclib.exe >> "$LOG_DIR/musiclib.log" 2>&1 &
  log "多音源已启动 pid=$!"
else
  log "!! 缺 qmusic-musiclib.exe"
fi

cd "$ROOT/server/node_modules/NeteaseCloudMusicApi" || exit 1
PORT=3301 HOST=127.0.0.1 nohup "$NODE_BIN" app.js >> "$LOG_DIR/ncm.log" 2>&1 &
log "网易云上游已启动 pid=$!"

cd "$ROOT/server" || exit 1
PORT=3300 NCM_PORT=3301 MUSICLIB_URL=http://127.0.0.1:3100 \
  nohup "$NODE_BIN" index.js >> "$LOG_DIR/gateway.log" 2>&1 &
log "网关已启动 pid=$!"

for p in 3100 3301 3300; do
  ok=0
  for i in $(seq 1 40); do
    if listening "$p"; then ok=1; break; fi
    sleep 1
  done
  [ "$ok" = "1" ] && log ":$p 就绪" || log "!! :$p 启动超时"
done

# ---------- 3. 隧道 ----------
NEW_URL=""
if [ "$USE_TUNNEL" -eq 1 ]; then
  TUNNEL_LOG="$ROOT/.workbuddy/tunnel-${STAMP}.log"
  cd "$ROOT" || exit 1
  nohup ./.workbuddy/cloudflared.exe tunnel --url http://127.0.0.1:3300 \
    --no-autoupdate --logfile "$TUNNEL_LOG" >> "$LOG_DIR/cloudflared.log" 2>&1 &
  log "隧道启动中，日志 $TUNNEL_LOG"
  for i in $(seq 1 45); do
    NEW_URL=$(grep -oE "https://[a-z0-9-]+\.trycloudflare\.com" "$TUNNEL_LOG" 2>/dev/null | tail -1)
    [ -n "$NEW_URL" ] && break
    sleep 2
  done
  if [ -n "$NEW_URL" ]; then
    log "隧道域名 $NEW_URL"
  else
    log "!! 90 秒内未拿到隧道域名"
  fi
fi

# ---------- 4. 更新并推送线上地址 ----------
if [ -n "$NEW_URL" ] && [ "$USE_PUSH" -eq 1 ]; then
  LAN_IP=$("$NODE_BIN" -e "const os=require('os');const n=os.networkInterfaces();for(const k in n){for(const a of n[k]){if(a.family==='IPv4'&&!a.internal&&a.address.startsWith('192.168.')){console.log(a.address);process.exit(0)}}}" 2>/dev/null)
  LAN_IP=${LAN_IP:-127.0.0.1}
  cd "$ROOT" || exit 1
  "$NODE_BIN" -e "
    const fs=require('fs');
    const p='server-address.json';
    const j=JSON.parse(fs.readFileSync(p,'utf8'));
    const url=process.argv[1]+'/';
    j.online=url;
    j.lan='http://'+process.argv[2]+':3300/';
    j.candidates=[url, j.lan];
    j.updated=new Date().toISOString().slice(0,10);
    fs.writeFileSync(p, JSON.stringify(j,null,2)+'\n');
    console.log('已写入', url);
  " "$NEW_URL" "$LAN_IP" >> "$RUN_LOG" 2>&1
  if bash .workbuddy/gh_api_push_direct.sh >> "$RUN_LOG" 2>&1; then
    log "配置已推送 GitHub"
  else
    log "!! 配置推送失败（见 $RUN_LOG）"
  fi
fi

# ---------- 5. 自检 ----------
sleep 3
"$NODE_BIN" "$ROOT/scripts/server-health.mjs" >> "$RUN_LOG" 2>&1
grep -E "^\[(OK|FAIL)" "$RUN_LOG" | tail -6

log "=== 完成，日志 $RUN_LOG ==="
