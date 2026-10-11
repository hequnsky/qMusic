#!/usr/bin/env bash
# 歌词页真机验证：播放 -> 等唱到中段 -> 开歌词页 -> 暂停 -> 同一时刻抓 UI + 截图 + 日志。
# 用法: bash scripts/lyric-verify.sh [播放后等待秒数] [采样次数]
#
# 注意：下面的点击坐标与「视口中心 1309」都是 1240x2772 那台真机上的实测值
# （歌词区高 2618 = 2772 - 导航栏 154，中心 1309）。换设备需重新量。
set -u
cd "$(dirname "$0")/.."
# adb 路径可用环境变量覆盖：ADB=/path/to/adb bash scripts/lyric-verify.sh
ADB="${ADB:-/e/Program Files/android/sdk/platform-tools/adb}"
PRE="${1:-22}"
N="${2:-1}"
APK="${APK:-android/app/build/outputs/apk/debug/qMusic-1.09-debug.apk}"
DIR="docs/screenshots/v109"
mkdir -p "$DIR"

# SystemUI 的媒体通知日志会刷爆 logcat 环形缓冲，读日志必须放大行数再过滤
logs() { MSYS_NO_PATHCONV=1 "$ADB" logcat -d -t 20000 2>&1 | grep "LYRIC" | grep -vE "tick|render" | tail -4; }

# 这里不加 MSYS_NO_PATHCONV：相对路径原样传给 adb，换机器也不用改
"$ADB" install -r -t "$APK" 2>&1 | tail -1
MSYS_NO_PATHCONV=1 "$ADB" shell am force-stop com.qmusic
MSYS_NO_PATHCONV=1 "$ADB" logcat -c
MSYS_NO_PATHCONV=1 "$ADB" shell am start -n com.qmusic/.MainActivity >/dev/null 2>&1
sleep 5
# 等到播放页进度真正走过 N 秒再进歌词页。
# 否则歌曲还在拉流/缓冲，currentLyricIndex 一直是 -1，歌词区一行白字都没有，
# 采到的就是「没有高亮」的假失败。
wait_playing() {
  local i pos sec
  for i in $(seq 1 24); do
    MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/pos.xml >/dev/null 2>&1
    MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/pos.xml "$DIR/pos.xml" >/dev/null 2>&1
    pos=$(grep -o 'text="[0-9][0-9]:[0-9][0-9]"' "$DIR/pos.xml" | head -1 | tr -dc '0-9:')
    if [ ${#pos} -eq 5 ] && [ $((10#${pos:0:2} * 60 + 10#${pos:3:2})) -ge "$PRE" ]; then
      echo "  已开唱 $pos（等待目标 ${PRE}s）"
      return 0
    fi
    sleep 5
  done
  echo "  [WARN] 等待开唱超时（最后读到 ${pos:-无}）"
  return 1
}

MSYS_NO_PATHCONV=1 "$ADB" shell input tap 1031 679   # 首页推荐卡片播放键
sleep 3
wait_playing
MSYS_NO_PATHCONV=1 "$ADB" shell input tap 946 258    # 播放页「歌词」
sleep 3

for i in $(seq 1 "$N"); do
  tag="s$i"
  # 先暂停：截图与 dump 之间有 ~1s 间隔，歌词每 2~3s 跳一行，不暂停会有假偏差
  MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 127 >/dev/null 2>&1
  sleep 1
  MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/ui.xml "$DIR/$tag.xml" >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" shell screencap -p /sdcard/screen.png >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/screen.png "$DIR/$tag.png" >/dev/null 2>&1
  echo "===== 采样 $tag ====="
  python scripts/lyric-check.py "$DIR/$tag.png" "$DIR/$tag.xml" 1309
  echo "  -- 日志尾部 --"
  logs
  if [ "$i" -lt "$N" ]; then
    MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 126 >/dev/null 2>&1   # 继续播放
    sleep 6
  fi
done
