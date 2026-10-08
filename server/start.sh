#!/usr/bin/env bash
# qMusic 音乐抓取服务启动脚本 (macOS/Linux)
# 默认监听 http://0.0.0.0:3000
cd "$(dirname "$0")"
if [ ! -d node_modules ]; then
  echo "[INFO] 正在安装依赖，首次运行请稍候..."
  npm install
fi
echo "[INFO] 启动 qMusic 音乐抓取服务 http://localhost:3000"
node node_modules/NeteaseCloudMusicApi/app.js