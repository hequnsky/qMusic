#!/usr/bin/env node
/**
 * uiautomator XML 解析小工具（纯计算，不调用任何外部进程）。
 *
 * 之所以拆成「bash 跑 adb + node 只解析文件」：
 * 某些受限环境下 node 无法 spawn 子进程，但 bash 可以；
 * 于是把「取界面树」交给 bash，把「找控件坐标」交给 node。
 *
 * 用法：
 *   node ui.js <dump.xml> list
 *   node ui.js <dump.xml> has  text  "发现"
 *   node ui.js <dump.xml> has  desc  "下一首"
 *   node ui.js <dump.xml> find text  "发现"      -> 输出 "cx cy" 或 "NOTFOUND"
 *   node ui.js <dump.xml> find desc  "下一首"
 *   node ui.js <dump.xml> count text "歌曲"
 */

const fs = require('fs')

const [, , file, cmd, kind, value] = process.argv

function parse(xml) {
  const nodes = []
  const re = /<node\b([^>]*?)\/?>/g
  let m
  while ((m = re.exec(xml)) !== null) {
    const attrs = {}
    const are = /([\w-]+)="([^"]*)"/g
    let a
    while ((a = are.exec(m[1])) !== null) attrs[a[1]] = a[2]
    const b = (attrs.bounds || '').match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/)
    const bounds = b
      ? { x1: +b[1], y1: +b[2], x2: +b[3], y2: +b[4] }
      : { x1: 0, y1: 0, x2: 0, y2: 0 }
    nodes.push({
      text: attrs.text || '',
      desc: attrs['content-desc'] || '',
      cls: attrs.class || '',
      clickable: attrs.clickable === 'true',
      bounds,
    })
  }
  return nodes
}

if (!file || !fs.existsSync(file)) {
  console.error('usage: node ui.js <dump.xml> list|has|find|count [text|desc] <value>')
  process.exit(2)
}

const nodes = parse(fs.readFileSync(file, 'utf8'))
const center = (n) =>
  `${Math.round((n.bounds.x1 + n.bounds.x2) / 2)} ${Math.round((n.bounds.y1 + n.bounds.y2) / 2)}`

function match(n, kind, value) {
  if (!value) return false
  const hay = kind === 'desc' ? n.desc : n.text
  return hay === value || hay.includes(value)
}

switch (cmd) {
  case 'list': {
    for (const n of nodes) {
      if (!n.text && !n.desc) continue
      console.log(`${center(n)}  cls=${n.cls}  text="${n.text}"  desc="${n.desc}"`)
    }
    break
  }
  case 'has': {
    const hit = nodes.some((n) => match(n, kind, value))
    console.log(hit ? 'YES' : 'NO')
    break
  }
  case 'count': {
    console.log(nodes.filter((n) => match(n, kind, value)).length)
    break
  }
  case 'find': {
    // 优先取可点击的、面积最小的（最贴近目标）
    const hits = nodes.filter((n) => match(n, kind, value))
    if (hits.length === 0) {
      console.log('NOTFOUND')
      break
    }
    hits.sort((a, b) => {
      if (a.clickable !== b.clickable) return a.clickable ? -1 : 1
      const area = (n) => (n.bounds.x2 - n.bounds.x1) * (n.bounds.y2 - n.bounds.y1)
      return area(a) - area(b)
    })
    console.log(center(hits[0]))
    break
  }
  default:
    console.error('未知命令：' + cmd)
    process.exit(2)
}
