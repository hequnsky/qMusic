#!/usr/bin/env bash
# ==================================================================
# qMusic 真机调试助手（配合 debug 包使用）
#
#   scripts/dev.sh dump                刷新界面树
#   scripts/dev.sh tap  <文案> [描述]   点按控件
#   scripts/dev.sh shot <文件名>        截屏到 testing/shots/
#   scripts/dev.sh xy   <文案>          只打印坐标
#   scripts/dev.sh start                启动 App
#   scripts/dev.sh restart              强制重启 App
#   scripts/dev.sh reverse              配置 adb reverse tcp:3000
#
# 说明：node 在本环境无法 spawn 子进程，所以 adb 全部由 bash 直接调用，
#       node 只负责解析 uiautomator 的 XML。
# ==================================================================
set -u
cd "$(dirname "$0")/.."

export MSYS_NO_PATHCONV=1
ADB="${ADB:-adb}"
NODE_BIN="${NODE_BIN:-C:/Users/Administrator/.workbuddy/binaries/node/versions/22.22.2-6/node.exe}"
DUMPS="testing/dumps"
SHOTS="testing/shots"
PKG="com.qmusic"

dump_tree() {
  mkdir -p "$DUMPS"
  $ADB shell "uiautomator dump /sdcard/ui.xml" >/dev/null 2>&1
  $ADB pull /sdcard/ui.xml "$DUMPS/ui.xml" >/dev/null 2>&1
}

find_xy() {
  local text="$1" desc="${2:-}"
  [ -f "$DUMPS/ui.xml" ] || dump_tree
  "$NODE_BIN" scripts/atap.js "$text" "$desc"
}

cmd="${1:-}"
shift || true

case "$cmd" in
  dump)    dump_tree; echo "已刷新 $DUMPS/ui.xml" ;;
  xy)      dump_tree; find_xy "${1:-}" "${2:-}" ;;
  tap)
    dump_tree
    xy="$(find_xy "${1:-}" "${2:-}")"
    if [ "$xy" = "NOTFOUND" ] || [ -z "$xy" ]; then
      echo "[tap] 未找到：${1:-}"; exit 1
    fi
    echo "[tap] ${1:-} -> $xy"
    $ADB shell input tap $xy
    ;;
  shot)
    mkdir -p "$SHOTS"
    name="${1:-shot.png}"
    $ADB shell "screencap -p /sdcard/s.png"
    $ADB pull /sdcard/s.png "$SHOTS/$name" >/dev/null 2>&1
    echo "$SHOTS/$name"
    ;;
  start)   $ADB shell am start -n "$PKG/.MainActivity" >/dev/null; echo "已启动" ;;
  restart)
    $ADB shell am force-stop "$PKG"
    sleep 1
    $ADB shell am start -n "$PKG/.MainActivity" >/dev/null
    echo "已重启"
    ;;
  reverse) $ADB reverse tcp:3000 tcp:3000 && echo "adb reverse tcp:3000 -> ok" ;;
  *) sed -n '2,20p' "$0" ;;
esac
