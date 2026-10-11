@echo off
REM 编译多音源聚合服务（Windows）
setlocal
cd /d "%~dp0"

where go >nul 2>nul
if errorlevel 1 (
  echo [ERROR] 未找到 go 命令，请先安装 Go 1.24+ 并加入 PATH
  exit /b 1
)

set GOPROXY=https://goproxy.cn,direct
set GOSUMDB=off
set GOTOOLCHAIN=local

echo [INFO] 正在编译 qmusic-musiclib.exe ...
go build -o qmusic-musiclib.exe .
if errorlevel 1 (
  echo [ERROR] 编译失败
  exit /b 1
)
echo [OK] 产物：%cd%\qmusic-musiclib.exe
endlocal
