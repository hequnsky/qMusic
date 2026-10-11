#!/usr/bin/env bash
# 编译多音源聚合服务（macOS / Linux）
set -e
cd "$(dirname "$0")"

if ! command -v go >/dev/null 2>&1; then
  echo "[ERROR] 未找到 go 命令，请先安装 Go 1.24+"
  exit 1
fi

export GOPROXY=https://goproxy.cn,direct
export GOSUMDB=off
export GOTOOLCHAIN=local

echo "[INFO] 正在编译 qmusic-musiclib ..."
go build -o qmusic-musiclib .
echo "[OK] 产物：$(pwd)/qmusic-musiclib"
