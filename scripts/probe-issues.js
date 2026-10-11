'use strict'
/**
 * 排查 5 个反馈问题的数据侧证据：
 *   1) /toplist 前 8 个榜单到底是什么（是否以 DJ 为主）
 *   2) /personalized?limit=10 返回的歌单顺序（换一换为什么没变化）
 *   3) /multi/sources 是否可用（音源切换条为什么没出现）
 */
const BASE = process.env.BASE || 'http://127.0.0.1:3000'

async function get(path) {
  const t0 = Date.now()
  try {
    const r = await fetch(BASE + path, { signal: AbortSignal.timeout(15000) })
    const text = await r.text()
    let json = null
    try { json = JSON.parse(text) } catch (_) {}
    return { ok: r.ok, status: r.status, ms: Date.now() - t0, json, text: text.slice(0, 300) }
  } catch (e) {
    return { ok: false, error: e.message, ms: Date.now() - t0 }
  }
}

async function main() {
  // 1) 排行榜
  const tl = await get('/toplist')
  console.log('=== /toplist ===', tl.ok ? `HTTP ${tl.status} ${tl.ms}ms` : tl.error)
  if (tl.json && Array.isArray(tl.json.list)) {
    console.log('榜单总数:', tl.json.list.length)
    console.log('前 12 个:')
    tl.json.list.slice(0, 12).forEach((t, i) => console.log(`  ${i + 1}. [${t.id}] ${t.name}  (${t.updateFrequency})`))
    const dj = tl.json.list.filter((t) => /DJ|dj|电音|慢摇|蹦迪/.test(t.name))
    console.log('含 DJ/电音关键词的榜单数:', dj.length)
    console.log('全部榜单名:', tl.json.list.map((t) => t.name).join(' | '))
  } else {
    console.log('原始:', tl.text)
  }

  // 2) 每日推荐（换一换）
  const p10 = await get('/personalized?limit=10')
  console.log('\n=== /personalized?limit=10 ===', p10.ok ? `HTTP ${p10.status} ${p10.ms}ms` : p10.error)
  if (p10.json && Array.isArray(p10.json.result)) {
    console.log('返回歌单数:', p10.json.result.length)
    p10.json.result.forEach((p, i) => console.log(`  ${i + 1}. [${p.id}] ${p.name} (${p.playCount})`))
  } else {
    console.log('原始:', p10.text)
  }

  // 3) 多音源
  const src = await get('/multi/sources')
  console.log('\n=== /multi/sources ===', src.ok ? `HTTP ${src.status} ${src.ms}ms` : src.error)
  console.log('原始:', JSON.stringify(src.json) || src.text)

  // 4) 顺带看一眼网关健康
  const root = await get('/')
  console.log('\n=== / ===', root.ok ? `HTTP ${root.status} ${root.ms}ms` : root.error)
}

main()
