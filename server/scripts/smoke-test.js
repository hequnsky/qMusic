#!/usr/bin/env node
/**
 * qMusic 音乐服务冒烟测试
 *
 * 依次验证：
 *   1. 网易云接口（网关代理）
 *   2. 多音源来源列表
 *   3. 各音源搜索
 *   4. 播放地址解析（直链 / 回源）
 *   5. 歌词
 *
 * 用法：
 *   node scripts/smoke-test.js                 # 默认打 http://127.0.0.1:3000
 *   BASE=http://192.168.1.5:3000 node scripts/smoke-test.js
 */

const BASE = (process.env.BASE || 'http://127.0.0.1:3000').replace(/\/+$/, '')
const KEYWORD = process.env.KEYWORD || '海阔天空'
const TIMEOUT = Number(process.env.TIMEOUT || 25000)

let pass = 0
let fail = 0
const rows = []

function record(name, ok, detail) {
  rows.push({ name, ok, detail })
  if (ok) pass++
  else fail++
  const tag = ok ? 'PASS' : 'FAIL'
  console.log(`  [${tag}] ${name}${detail ? ' - ' + detail : ''}`)
}

async function getJSON(path, timeout = TIMEOUT) {
  const res = await fetch(BASE + path, { signal: AbortSignal.timeout(timeout) })
  const text = await res.text()
  try {
    return { status: res.status, json: JSON.parse(text) }
  } catch (_) {
    return { status: res.status, json: null, text }
  }
}

async function main() {
  console.log(`\n=== qMusic 服务冒烟测试  BASE=${BASE}  关键词="${KEYWORD}" ===\n`)

  // 1. 网易云接口
  console.log('1) 网易云接口（网关 → :3001）')
  try {
    const r = await getJSON(`/search?keywords=${encodeURIComponent(KEYWORD)}&limit=3`)
    const n = r.json?.result?.songs?.length ?? 0
    record('GET /search', n > 0, `返回 ${n} 首`)
  } catch (e) {
    record('GET /search', false, String(e.message || e))
  }

  // 2. 多音源列表
  console.log('\n2) 多音源服务')
  let sources = []
  try {
    const r = await getJSON('/multi/sources')
    sources = r.json?.sources || []
    record('GET /multi/sources', r.json?.available === true, `可用音源 ${sources.length} 个`)
  } catch (e) {
    record('GET /multi/sources', false, String(e.message || e))
  }

  if (sources.length === 0) {
    console.log('\n  多音源服务未启动，跳过后续用例（这是允许的降级状态）')
  } else {
    const targets = sources.filter((s) => s.id !== 'apple')
    for (const s of targets) {
      console.log(`\n3) 音源 ${s.id}（${s.name}）`)
      let token = null
      try {
        const r = await getJSON(`/multi/search?source=${s.id}&keyword=${encodeURIComponent(KEYWORD)}&limit=5`)
        const songs = r.json?.songs || []
        token = songs[0]?.token || null
        record(`搜索 ${s.id}`, songs.length > 0, `返回 ${songs.length} 首`)
      } catch (e) {
        record(`搜索 ${s.id}`, false, String(e.message || e))
        continue
      }
      if (!token) continue

      try {
        const r = await getJSON(`/multi/song/url?s=${encodeURIComponent(token)}`)
        const url = r.json?.url
        record(`取播放地址 ${s.id}`, !!url, url ? `${r.json.proxied ? '回源' : '直链'} ${String(url).slice(0, 60)}...` : r.json?.error)
      } catch (e) {
        record(`取播放地址 ${s.id}`, false, String(e.message || e))
      }

      try {
        const r = await getJSON(`/multi/lyric?s=${encodeURIComponent(token)}`)
        const lrc = r.json?.lrc || ''
        record(`取歌词 ${s.id}`, lrc.length > 0, lrc.length ? `${lrc.length} 字符` : '无歌词')
      } catch (e) {
        record(`取歌词 ${s.id}`, false, String(e.message || e))
      }
    }
  }

  console.log('\n================ 汇总 ================')
  console.log(`  通过 ${pass}   失败 ${fail}   共 ${rows.length}`)
  const failed = rows.filter((r) => !r.ok)
  if (failed.length) {
    console.log('  失败用例：')
    for (const f of failed) console.log(`    - ${f.name}: ${f.detail}`)
  }
  console.log('======================================\n')
  process.exit(fail > 0 ? 1 : 0)
}

main().catch((e) => {
  console.error('冒烟测试异常：', e)
  process.exit(1)
})
