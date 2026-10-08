@echo off
REM qMusic 音乐抓取服务启动脚本 (Windows)
REM 默认监听 http://0.0.0.0:3000
cd /d "%~dp0"
if not exist node_modules (
  echo [INFO] 正在安装依赖，首次运行请稍候...
  call npm install
)
echo [INFO] 启动 qMusic 音乐抓取服务 http://localhost:3000
node node_modules/NeteaseCloudMusicApi/app.js