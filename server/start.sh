#!/usr/bin/env bash
# qMusic 音乐抓取服务启动脚本 (macOS/Linux)
#   网关    http://0.0.0.0:3000   <- Android 客户端填这个
#   网易云  http://127.0.0.1:3001 （内网，由网关自动启动）
#   多音源  http://127.0.0.1:3100 （Go + music-lib，已编译则自动启动）
cd "$(dirname "$0")"

if [ ! -d node_modules ]; then
  echo "[INFO] 正在安装 Node 依赖，首次运行请稍候..."
  npm install
fi

EXE="musiclib-service/qmusic-musiclib"
[ -x "$EXE.exe" ] && EXE="$EXE.exe"

if [ -x "$EXE" ]; then
  echo "[INFO] 启动多音源服务 http://127.0.0.1:3100"
  "$EXE" > /tmp/qmusic-musiclib.log 2>&1 &
else
  echo "[WARN] 未找到 musiclib-service 可执行文件，多音源功能将不可用"
  echo "       编译方式见 server/musiclib-service/README.md"
fi

echo "[INFO] 启动 qMusic 音乐服务网关 http://localhost:3000"
exec node index.js
