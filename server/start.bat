@echo off
REM qMusic 音乐抓取服务启动脚本 (Windows)
REM   网关      http://0.0.0.0:3000   <- Android 客户端填这个
REM   网易云    http://127.0.0.1:3001 （内网，由网关自动启动）
REM   多音源    http://127.0.0.1:3100 （Go + music-lib，已编译则自动启动）
setlocal
cd /d "%~dp0"

if not exist node_modules (
  echo [INFO] 正在安装 Node 依赖，首次运行请稍候...
  call npm install
)

REM ---- 多音源服务（可选）----
if exist "musiclib-service\qmusic-musiclib.exe" (
  echo [INFO] 启动多音源服务 http://127.0.0.1:3100
  start "qmusic-musiclib" /min "musiclib-service\qmusic-musiclib.exe"
) else (
  echo [WARN] 未找到 musiclib-service\qmusic-musiclib.exe，多音源功能将不可用
  echo        编译方式见 server\musiclib-service\README.md
)

echo [INFO] 启动 qMusic 音乐服务网关 http://localhost:3000
node index.js
endlocal
