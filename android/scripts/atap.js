#!/usr/bin/env node
'use strict'
/**
 * 在真机上按文案/描述定位控件并输出可点击的中心坐标。
 *
 *   node scripts/atap.js <文本> [描述]
 * 输出： "x y"（可直接喂给 adb shell input tap）
 *
 * 依赖已 dump 到 testing/dumps/ui.xml 的界面树；未提供则自动执行 uiautomator dump。
 */
const fs = require('fs')
const path = require('path')

const XML = path.join(__dirname, '..', 'testing', 'dumps', 'ui.xml')

function nodes(xml) {
  const out = []
  const re = /<node[^>]*\/?>/g
  let m
  while ((m = re.exec(xml))) {
    const tag = m[0]
    const get = (k) => (tag.match(new RegExp(k + '="([^"]*)"')) || [])[1] || ''
    const b = tag.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/)
    if (!b) continue
    const [x1, y1, x2, y2] = [Number(b[1]), Number(b[2]), Number(b[3]), Number(b[4])]
    out.push({
      text: get('text'),
      desc: get('content-desc'),
      clickable: get('clickable') === 'true',
      x1, y1, x2, y2,
      cx: Math.round((x1 + x2) / 2),
      cy: Math.round((y1 + y2) / 2),
      area: (x2 - x1) * (y2 - y1),
    })
  }
  return out
}

function main() {
  const text = process.argv[2]
  const desc = process.argv[3]
  if (!text && !desc) {
    console.error('用法: node scripts/atap.js <文本> [描述]')
    process.exit(2)
  }
  if (!fs.existsSync(XML)) {
    console.error('[atap] 缺少 testing/dumps/ui.xml，请先执行：')
    console.error('  MSYS_NO_PATHCONV=1 adb shell "uiautomator dump /sdcard/ui.xml" && \\')
    console.error('  MSYS_NO_PATHCONV=1 adb pull /sdcard/ui.xml testing/dumps/ui.xml')
    process.exit(3)
  }
  const list = nodes(fs.readFileSync(XML, 'utf8'))

  let hit = list.filter((n) =>
    (text && n.text.includes(text)) || (desc && n.desc.includes(desc)))
  if (!hit.length) { console.log('NOTFOUND'); process.exit(1) }

  // 优先可点击、面积最小（最贴近文字本身的那个节点）
  hit.sort((a, b) => (Number(b.clickable) - Number(a.clickable)) || (a.area - b.area))
  const n = hit[0]
  console.log(`${n.cx} ${n.cy}`)
}

main()
