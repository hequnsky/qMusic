#!/usr/bin/env bash
# 干净复现「歌词页开着 + 切实切歌 → 歌词应重新居中（含高亮当前行）」。
# 关键校验：
#   1) 切歌后 EFFECT-ENTER 是否出现了新 song（证明 effect 用对象引用作 key 后必然重触发）；
#   2) 等新歌开唱后，白色高亮当前行是否回到屏幕中心(~1309)，而不是停在中下方。
set -u
cd "$(dirname "$0")/.."
ADB="/e/Program Files/android/sdk/platform-tools/adb"
APK="android/dist/qMusic-1.10-debug.apk"
DIR="docs/screenshots/v109"
mkdir -p "$DIR"

MSYS_NO_PATHCONV=1 "$ADB" install -r -t "$APK" 2>&1 | tail -1
MSYS_NO_PATHCONV=1 "$ADB" shell am force-stop com.qmusic
MSYS_NO_PATHCONV=1 "$ADB" logcat -c
MSYS_NO_PATHCONV=1 "$ADB" shell am start -n com.qmusic/.MainActivity >/dev/null 2>&1
sleep 4
MSYS_NO_PATHCONV=1 "$ADB" shell input tap 1031 679   # 主页推荐卡片播放
sleep 6

# 在播放页连续下一首，直到打开歌词页能看到 >=6 行歌词（真歌词）
for i in $(seq 1 8); do
  MSYS_NO_PATHCONV=1 "$ADB" shell input tap 858 2423 && sleep 5     # 播放页下一首
  MSYS_NO_PATHCONV=1 "$ADB" shell input tap 946 258 && sleep 4       # 打开歌词页
  MSYS_NO_PATHCONV=1 "$ADB" shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/ui.xml "$DIR/find.xml" >/dev/null 2>&1
  cnt=$(node scripts/ui.js "$DIR/find.xml" | awk -F'c=' '{s=$2; y=s+0; if(y>320 && y<2620) c++} END{print c+0}')
  echo "round $i: lyricNodes=$cnt"
  if [ "$cnt" -ge 6 ]; then echo "FOUND real-lyric song"; break; fi
  MSYS_NO_PATHCONV=1 "$ADB" shell input tap 112 258 && sleep 1       # 返回播放页
done

# 记录切歌前 effect 的 song id（不清日志，便于前后比对）
before_song=$(MSYS_NO_PATHCONV=1 "$ADB" logcat -d -t 30000 2>&1 | grep "ENTER" | grep -oE "song=[0-9]+" | tail -1)
echo "before_effect_song=$before_song"

echo "=== 通知栏切歌（重试直到 effect 的 song 变化）==="
for t in 1 2 3 4 5; do
  MSYS_NO_PATHCONV=1 "$ADB" shell input swipe 620 10 620 1100 && sleep 2
  MSYS_NO_PATHCONV=1 "$ADB" shell input tap 501 721 && sleep 2
  MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 4 && sleep 1
  sleep 5
  cur_song=$(MSYS_NO_PATHCONV=1 "$ADB" logcat -d -t 30000 2>&1 | grep "ENTER" | grep -oE "song=[0-9]+" | tail -1)
  echo "attempt $t: latest_effect_song=$cur_song"
  if [ "$cur_song" != "$before_song" ] && [ -n "$cur_song" ]; then echo "SONG/TRACK CHANGED -> effect 重触发到新歌"; break; fi
  MSYS_NO_PATHCONV=1 "$ADB" shell input keyevent 4 >/dev/null 2>&1; sleep 1
done

echo "=== 等切后的新歌开唱 -> 抓高亮当前行(白字)是否居中 ==="
for w in $(seq 1 12); do
  MSYS_NO_PATHCONV=1 "$ADB" shell screencap -p /sdcard/after.png >/dev/null 2>&1
  MSYS_NO_PATHCONV=1 "$ADB" pull /sdcard/after.png "$DIR/after.png" >/dev/null 2>&1
  res=$(python scripts/rowscan.py "$DIR/after.png" 320 2620 150 | grep -i "WHITE")
  if [ -n "$res" ]; then echo "ROUND $w 出现高亮白字行:"; echo "$res"; break; fi
  echo "ROUND $w: 仍无高亮白字行(前奏中)，继续等待..."; sleep 5
done

echo "--- 切歌后当前行落点(白色高亮行) ---"
python scripts/rowscan.py "$DIR/after.png" 320 2620 150 | grep -iE "white|center"
echo "--- 切歌后 EFFECT-ENTER 出现过的 song（应含新歌 => 证明重触发）---"
MSYS_NO_PATHCONV=1 "$ADB" logcat -d -t 30000 2>&1 | grep "ENTER" | grep -oE "song=[0-9]+" | sort -u
echo "--- 末尾 DONE 居中结果 ---"
MSYS_NO_PATHCONV=1 "$ADB" logcat -d -t 30000 2>&1 | grep "DONE" | tail -4
