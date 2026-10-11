#!/usr/bin/env bash
# ==================================================================
# qMusic 真机 10 轮批量功能测试
#
# 以「真实用户」路径驱动设备：
#   启动 → 发现页 → 播放 → 切歌 → 暂停/继续 → 歌词 → 收藏
#   → 搜索（含音源切换）→ 我的 → 我喜欢的 / 我的下载 → 桌面小组件
#
# 用法： bash scripts/batch-test.sh [轮数]
# 产物： testing/report-rounds.md   （汇总报告）
#        testing/rounds/round-N.log （每轮明细）
#        testing/shots/…            （关键步骤截图）
# ==================================================================
set -u
cd "$(dirname "$0")/.." || exit 1
source scripts/env.sh

ROUNDS="${1:-10}"
OUT="testing/report-rounds.md"
mkdir -p testing/rounds testing/shots

TOTAL_PASS=0
TOTAL_FAIL=0
declare -a ROUND_LINES

log()  { echo "$@" | tee -a "$CUR_LOG"; }
hdr()  { log ""; log "───── $* ─────"; }

echo "qMusic 批量功能测试：$ROUNDS 轮" | tee "$OUT" >/dev/null
{
  echo ""
  echo "| 轮次 | 通过 | 失败 | 耗时 | 结果 |"
  echo "|---|---|---|---|---|"
} >> "$OUT"

for r in $(seq 1 "$ROUNDS"); do
  CUR_LOG="testing/rounds/round-$r.log"
  : > "$CUR_LOG"
  PASS=0; FAIL=0; FAILED=()
  START=$(date +%s)

  log "=================================================="
  log "  第 $r 轮  $(date '+%H:%M:%S')"
  log "=================================================="

  # ---------------------------------------------------------------
  # 1. 冷启动 + 发现页
  # ---------------------------------------------------------------
  hdr "1 冷启动 / 发现页"
  A shell am force-stop "$PKG" >/dev/null 2>&1
  sleep 2
  A shell am start -n "$PKG/.MainActivity" >/dev/null 2>&1
  sleep 7
  dump || true
  # 首次安装会出现隐私弹窗
  if [ "$(uihas 同意并继续)" = "YES" ]; then
    log "  · 出现隐私弹窗，点击同意"
    tapText "同意并继续" || true
    sleep 3
    dump || true
  fi
  assert "发现页加载（标题「汽水音乐」）" "YES" "$(uihas 汽水音乐)"
  assert "发现页有搜索入口"             "YES" "$(uihasd 搜索)"
  assert "发现页有排行榜模块"           "YES" "$(uihas 排行榜)"
  assert "底部导航齐全（发现/播放/我的）" "YES" "$(uihas 我的)"
  assert "应用进程存活"                 "YES" "$(alive)"
  shot "r${r}_01_discover"

  # ---------------------------------------------------------------
  # 2. 播放（推荐歌单）
  # ---------------------------------------------------------------
  hdr "2 播放"
  tapXY 1031 679          # 发现页横幅上的播放按钮
  sleep 8
  dump || true
  assert "进入播放页（正在播放）" "YES" "$(uihas 正在播放)"
  assert "播放状态为 PLAYING"     "PLAYING" "$(playState)"
  A0="$(nowPlaying)"
  log "  · 当前曲目：$A0"
  assert "拿到当前曲目名" "YES" "$([ -n "$A0" ] && echo YES || echo NO)"
  # 播放位置推进
  dump || true
  P1="$(uilist | grep -oE 'text="[0-9]{2}:[0-9]{2}"' | head -1)"
  sleep 5
  dump || true
  P2="$(uilist | grep -oE 'text="[0-9]{2}:[0-9]{2}"' | head -1)"
  log "  · 进度：$P1 → $P2"
  assert "播放进度在推进" "NO" "$([ "$P1" = "$P2" ] && echo YES || echo NO)"
  shot "r${r}_02_player"

  # ---------------------------------------------------------------
  # 3. 封面（通知栏大图 = 封面已成功加载）
  # ---------------------------------------------------------------
  hdr "3 封面（通知栏大图）"
  assert "通知栏已带上封面大图" "YES" "$(waitLargeIcon)"

  # ---------------------------------------------------------------
  # 4. 暂停 / 继续 / 上一首 / 下一首
  # ---------------------------------------------------------------
  hdr "4 播放控制"
  tapXY 621 2199          # 中央播放/暂停
  sleep 5
  assert "点击暂停后为 PAUSED" "PAUSED" "$(playState)"
  tapXY 621 2199
  sleep 6
  assert "点击播放后为 PLAYING" "PLAYING" "$(playState)"

  A1="$(nowPlaying)"
  tapXY 858 2199          # 下一首
  sleep 6
  A2="$(nowPlaying)"
  log "  · 下一首：$A1 → $A2"
  assert "下一首切换了曲目" "NO" "$([ "$A1" = "$A2" ] && echo YES || echo NO)"

  tapXY 385 2199          # 上一首
  sleep 6
  A3="$(nowPlaying)"
  log "  · 上一首：$A2 → $A3"
  assert "上一首切换了曲目" "NO" "$([ "$A2" = "$A3" ] && echo YES || echo NO)"

  # ---------------------------------------------------------------
  # 5. 歌词
  # ---------------------------------------------------------------
  hdr "5 歌词"
  tapXY 946 224            # 歌词入口
  sleep 5
  dump || true
  NO_LYRIC="$(uihas 暂无歌词)"
  log "  · 暂无歌词 = $NO_LYRIC"
  assert "歌词页有歌词内容（非「暂无歌词」）" "NO" "$NO_LYRIC"
  shot "r${r}_03_lyric"
  tapXY 112 224            # 返回
  sleep 3

  # ---------------------------------------------------------------
  # 6. 收藏
  # ---------------------------------------------------------------
  hdr "6 收藏"
  dump || true
  if [ "$(uihas 已喜欢)" = "YES" ]; then
    log "  · 已处于喜欢状态，先取消再收藏"
    tapXY 620 1817
    sleep 3
  fi
  tapXY 620 1817
  sleep 3
  dump || true
  assert "收藏后显示「已喜欢」" "YES" "$(uihas 已喜欢)"

  # ---------------------------------------------------------------
  # 7. 搜索 + 音源切换
  # ---------------------------------------------------------------
  hdr "7 搜索 / 音源切换"
  gotoTab "发现" "排行榜" || true
  dump || true
  # 搜索图标偶尔需要点第二次（首屏还在做入场动画时会吞掉第一次点击）
  OPENED="NO"
  for _ in 1 2 3; do
    tapDesc "搜索" || true
    sleep 5
    dump || true
    if [ "$(uihas 搜索歌曲)" = "YES" ]; then OPENED="YES"; break; fi
  done
  assert "打开搜索页"       "YES" "$OPENED"
  assert "音源切换条已渲染" "YES" "$(uihas 网易云音乐)"
  shot "r${r}_04_search"

  # 输入关键词（ASCII，避免中文输入法依赖）
  tapXY 523 367; sleep 2
  dump || true
  # 搜索框可能残留上一次的关键词，先用「清空」按钮清掉，避免追加拼接
  if [ "$(uihasd 清空)" = "YES" ]; then
    tapDesc "清空" || true
    sleep 1
  fi
  tapXY 523 367; sleep 1
  A shell input text "Jay"; sleep 2
  A shell input keyevent 66; sleep 6
  dump || true
  log "  · 搜索框内容：$(uilist | grep EditText | head -1 | sed -E 's/.*text="([^"]*)".*/\1/')"
  assert "搜索返回结果" "YES" "$(uihas 找到)"

  # 切到 QQ 音乐
  tapXY 615 511; sleep 6
  dump || true
  assert "切换音源后仍在搜索页" "YES" "$(uihas 音源)"
  shot "r${r}_05_source_switch"
  tapXY 314 511; sleep 5   # 切回网易云
  hideIme                  # 收起输入法，否则会遮住底部导航影响后续步骤

  # ---------------------------------------------------------------
  # 8. 我的页 & 收藏列表（回归：曾经的重复 key 崩溃）
  # ---------------------------------------------------------------
  hdr "8 我的页 / 收藏列表"
  gotoTab "发现" "排行榜" || true
  gotoTab "我的" "深色模式" || true
  dump || true
  assert "我的页加载"       "YES" "$(uihas 深色模式)"
  assert "我的页有最近播放" "YES" "$(uihas 最近播放)"
  assert "我的页有卡片入口" "YES" "$(uihas 我的下载)"
  shot "r${r}_06_mine"

  # 先测「我的下载」（此时列表未滚动，卡片坐标稳定）
  tapXY 913 1504
  sleep 5
  dump || true
  assert "我的下载页可打开" "YES" "$(uihas 我的下载)"
  shot "r${r}_06_downloads"
  tapXY 112 224            # 返回
  sleep 4

  # 再点「我喜欢的」卡片（会触发列表滚动 → 曾经崩溃的路径）
  dump || true
  tapXY 328 1147
  sleep 6
  dump || true
  assert "点击「我喜欢的」后应用未崩溃" "YES" "$(alive)"
  assert "收藏列表可见"                 "YES" "$(uihas 我喜欢的)"
  shot "r${r}_07_favorites"

  # ---------------------------------------------------------------
  # 9. 桌面小组件
  # ---------------------------------------------------------------
  hdr "9 桌面小组件"
  key 3; sleep 3
  # 找到小组件所在桌面页（最多翻 4 页）
  FOUND_W=false
  for _ in 1 2 3 4; do
    dump || true
    if [ "$(uihasd 播放/暂停)" = "YES" ]; then FOUND_W=true; break; fi
    A shell input swipe 1200 1400 100 1400 250; sleep 2
  done
  assert "桌面小组件存在" "YES" "$([ "$FOUND_W" = true ] && echo YES || echo NO)"
  if [ "$FOUND_W" = true ]; then
    assert "小组件有封面区"   "YES" "$(uihasd 专辑封面)"
    assert "小组件有上一首键" "YES" "$(uihasd 上一首)"
    assert "小组件有下一首键" "YES" "$(uihasd 下一首)"
    shot "r${r}_08_widget"

    WB="$(nowPlaying)"
    tapDesc "下一首" || true
    sleep 3
    tapDesc "下一首" || true
    sleep 4
    WA="$(nowPlaying)"
    log "  · 小组件下一首：$WB → $WA"
    assert "小组件「下一首」生效" "NO" "$([ "$WB" = "$WA" ] && echo YES || echo NO)"

    S1="$(playState)"
    tapDesc "播放/暂停" || true
    sleep 3
    S2="$(playState)"
    log "  · 小组件播放/暂停：$S1 → $S2"
    assert "小组件「播放/暂停」生效" "NO" "$([ "$S1" = "$S2" ] && echo YES || echo NO)"
  fi

  # ---------------------------------------------------------------
  # 10. 收尾
  # ---------------------------------------------------------------
  hdr "10 稳定性"
  assert "全程无崩溃，进程存活" "YES" "$(alive)"

  END=$(date +%s)
  DUR=$((END-START))
  TOTAL_PASS=$((TOTAL_PASS+PASS))
  TOTAL_FAIL=$((TOTAL_FAIL+FAIL))

  if [ "$FAIL" -eq 0 ]; then RES="✅ 全通过"; else RES="❌ $FAIL 项失败"; fi
  log ""
  log "第 $r 轮结果：通过 $PASS / 失败 $FAIL  （${DUR}s）$RES"
  if [ "$FAIL" -gt 0 ]; then
    log "失败项："
    for f in "${FAILED[@]}"; do log "   - $f"; done
  fi
  echo "| $r | $PASS | $FAIL | ${DUR}s | $RES |" >> "$OUT"
  if [ "$FAIL" -gt 0 ]; then
    { echo ""; echo "**第 $r 轮失败项：**"; for f in "${FAILED[@]}"; do echo "- ❌ $f"; done; } >> "$OUT"
  fi
done

# ------------------------------------------------------------------
# 汇总
# ------------------------------------------------------------------
{
  echo ""
  echo "## 汇总"
  echo ""
  echo "- 总轮次：**$ROUNDS**"
  echo "- 断言通过：**$TOTAL_PASS**"
  echo "- 断言失败：**$TOTAL_FAIL**"
  echo "- 通过率：**$(awk -v p="$TOTAL_PASS" -v f="$TOTAL_FAIL" 'BEGIN{ t=p+f; if(t==0) t=1; printf "%.1f%%", p*100/t }')**"
  echo ""
  if [ "$TOTAL_FAIL" -eq 0 ]; then
    echo "> ✅ 全部 $ROUNDS 轮功能测试通过。"
  else
    echo "> ⚠️ 存在失败项，请查看 testing/rounds/ 下各轮明细日志。"
  fi
} >> "$OUT"

echo ""
echo "=================================================="
echo "  测试完成：$ROUNDS 轮"
echo "  通过 $TOTAL_PASS / 失败 $TOTAL_FAIL"
echo "  报告：$OUT"
echo "=================================================="
