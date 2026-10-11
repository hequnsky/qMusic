#!/usr/bin/env bash
# 真机批量测试的公共环境与助手函数。
# 用法： source scripts/env.sh

export MSYS_NO_PATHCONV=1
ADB="/e/Program Files/android/sdk/platform-tools/adb.exe"
NODE="C:/Users/Administrator/.workbuddy/binaries/node/versions/22.22.2-6/node.exe"
DEV="-s d74f4906"
PKG="com.qmusic"
SRV_DIR="http://127.0.0.1:3000"

cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

A()      { "$ADB" $DEV "$@"; }
sh_()    { A shell "$@"; }

# 抓取界面树到 testing/dumps/ui.xml（重试 3 次）
dump() {
  for _ in 1 2 3; do
    if A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; then
      A pull /sdcard/ui.xml testing/dumps/ui.xml >/dev/null 2>&1 && return 0
    fi
    sleep 1
  done
  return 1
}

uihas()    { "$NODE" scripts/ui.js testing/dumps/ui.xml has  text "$1" 2>/dev/null; }
uihasd()   { "$NODE" scripts/ui.js testing/dumps/ui.xml has  desc "$1" 2>/dev/null; }
uifind()   { "$NODE" scripts/ui.js testing/dumps/ui.xml find text "$1" 2>/dev/null; }
uifindd()  { "$NODE" scripts/ui.js testing/dumps/ui.xml find desc "$1" 2>/dev/null; }
uicount()  { "$NODE" scripts/ui.js testing/dumps/ui.xml count text "$1" 2>/dev/null; }
uilist()   { "$NODE" scripts/ui.js testing/dumps/ui.xml list 2>/dev/null; }

# 按文案点击（find 返回 "cx cy"）
tapText() {
  local pos; pos="$(uifind "$1")"
  if [ "$pos" = "NOTFOUND" ] || [ -z "$pos" ]; then return 1; fi
  A shell input tap $pos
  sleep 1
  return 0
}
tapDesc() {
  local pos; pos="$(uifindd "$1")"
  if [ "$pos" = "NOTFOUND" ] || [ -z "$pos" ]; then return 1; fi
  A shell input tap $pos
  sleep 1
  return 0
}
tapXY() { A shell input tap "$1" "$2"; sleep 1; }

shot() {  # shot <name>
  A shell screencap -p /sdcard/shot.png >/dev/null 2>&1
  A pull /sdcard/shot.png "testing/shots/$1.png" >/dev/null 2>&1
}

# 高亮一个断言： assert <描述> <YES|NO 期望> <实际值>
PASS=0; FAIL=0
declare -a FAILED
assert() {
  local desc="$1" want="$2" got="$3" icon line
  if [ "$want" = "$got" ]; then
    PASS=$((PASS+1)); icon="✅"
    line="    ✅ $desc"
  else
    FAIL=$((FAIL+1)); FAILED+=("$desc"); icon="❌"
    line="    ❌ $desc  (期望 $want, 实际 $got)"
  fi
  echo "$line"
  # 同时落盘到当前轮次日志，保证报告可追溯
  [ -n "${CUR_LOG:-}" ] && echo "$line" >> "$CUR_LOG"
  return 0
}

key() { A shell input keyevent "$1"; sleep 1; }

# ---------- 播放状态断言辅助（读系统媒体会话，不依赖 UI 文案） ----------

# 当前 PlaybackState：PLAYING / PAUSED / NONE
# 注意：不能简单 grep 'state=[A-Z]+' —— 那样会先匹配到 "state=PlaybackState"。
playState() {
  A shell "dumpsys media_session 2>/dev/null | grep -m1 'state=PlaybackState'" \
    | grep -oE 'state=(PLAYING|PAUSED|STOPPED|BUFFERING|NONE|ERROR|CONNECTING|SKIPPING|FAST_FORWARDING|REWINDING)' \
    | head -1 | cut -d= -f2 | tr -d '\r'
}

# 当前曲目（媒体会话元数据里的标题）
nowPlaying() {
  A shell "dumpsys media_session 2>/dev/null | grep -m1 'metadata: size='" \
    | sed -E 's/.*description=([^,]*),.*/\1/' | tr -d '\r'
}

# 通知栏是否已带上封面大图（封面加载成功的客观证据）
# 注意：必须只解析「本应用」那条通知 —— 整个 dump 里第一条 largeIcon 往往是别的通知的 null。
hasLargeIcon() {
  local tmp ln v
  tmp="testing/dumps/notif.txt"
  mkdir -p testing/dumps
  A shell "dumpsys notification --noredact 2>/dev/null" > "$tmp" 2>/dev/null
  ln="$(grep -n "pkg=$PKG" "$tmp" | head -1 | cut -d: -f1)"
  if [ -n "$ln" ]; then
    v="$(awk -v s="$ln" 'NR>=s && /publicNotification/{exit} NR>=s{print}' "$tmp" \
         | grep -m1 'android.largeIcon=')"
  fi
  : > "$tmp"
  case "$v" in
    *null*)               echo "NO" ;;
    *android.largeIcon=*) echo "YES" ;;
    *)                    echo "NO" ;;
  esac
}

# 应用进程是否存活
alive() {
  if A shell "ps -A | grep -q $PKG" >/dev/null 2>&1; then echo "YES"; else echo "NO"; fi
}

# 当前前台包名
foreground() {
  A shell "dumpsys window 2>/dev/null | grep -m1 mCurrentFocus" | sed -E 's/.*u0 ([^ ]+).*/\1/' | tr -d '\r'
}

# 封面是异步加载的，轮询等待（最多 10 次 × 3s）
waitLargeIcon() {
  local i
  for i in $(seq 1 10); do
    if [ "$(hasLargeIcon)" = "YES" ]; then echo "YES"; return; fi
    sleep 3
  done
  echo "NO"
}

# 输入法是否弹出（弹出时会遮住底部导航，必须先收起）
imeVisible() {
  A shell "dumpsys input_method 2>/dev/null | grep -m1 'mInputShown='" 2>/dev/null \
    | grep -q "mInputShown=true" && echo YES || echo NO
}

# 收起输入法：仅当输入法确实弹出时才按一次返回键（返回键优先关输入法，不会退出页面）
hideIme() {
  local i
  for i in 1 2; do
    [ "$(imeVisible)" = "YES" ] || return 0
    A shell input keyevent 4 >/dev/null 2>&1
    sleep 2
  done
}

# 应用是否在前台（当前焦点窗口属于本应用）
inApp() {
  A shell "dumpsys window 2>/dev/null | grep -m1 mCurrentFocus" 2>/dev/null | grep -q "$PKG" && echo YES || echo NO
}

# 确保应用在前台；若播放页覆盖层开着先收起，输入法弹出则先收起
ensureApp() {
  if [ "$(inApp)" != "YES" ]; then
    A shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
    sleep 5
  fi
  hideIme
  dump || true
  if [ "$(uihasd 收起)" = "YES" ]; then
    tapXY 126 224     # 收起播放页
    sleep 3
    dump || true
  fi
}

# 点击底部导航（发现 / 播放 / 我的）。可选传入「期望出现的文案」用于校验是否真的切过去了。
# 注意：绝不盲目按返回键，否则会退出应用。
gotoTab() {
  local name="$1" expect="${2:-}" i
  for i in 1 2 3; do
    ensureApp
    if tapText "$name"; then
      sleep 3
      if [ -z "$expect" ]; then return 0; fi
      dump || true
      if [ "$(uihas "$expect")" = "YES" ]; then return 0; fi
    fi
    sleep 2
  done
  return 1
}

# 回到发现页（App 内导航归零）
gotoDiscover() { gotoTab "发现"; }

