'use strict'

/**
 * 多音源聚合路由（/multi/*）。
 *
 * 上游是 musiclib-service —— 一个基于开源项目
 * https://github.com/guohuiyuan/music-lib 的 Go 服务，
 * 统一了网易云 / QQ / 酷狗 / 酷我 / 咪咕 / 千千 / 汽水 / B站 / Apple Music
 * 的搜索、直链解析与歌词能力。
 *
 * 本层职责：
 *   1. 把 Android 客户端的扁平参数翻译成上游接口；
 *   2. 把上游生成的「回源播放地址」改写成对客户端真正可达的网关地址；
 *   3. 上游不可用时优雅降级（返回 503 + 明确原因，不影响原有的网易云接口）。
 */

const express = require('express')
const { Readable } = require('stream')
const { createMatcher, decodeSongToken } = require('./soda-match')

const GO_BASE = (process.env.MUSICLIB_URL || 'http://127.0.0.1:3100').replace(/\/+$/, '')
const TIMEOUT_MS = Number(process.env.MUSICLIB_TIMEOUT || 20000)

const router = express.Router()

/** 探测上游是否在线（带 1.5s 缓存，避免每次请求都打一次） */
let healthCache = { at: 0, ok: false, sources: [] }
async function probe(force = false) {
  const now = Date.now()
  if (!force && now - healthCache.at < 1500) return healthCache
  try {
    const res = await fetch(`${GO_BASE}/health`, { signal: AbortSignal.timeout(2500) })
    const json = await res.json()
    let sources = []
    try {
      const sres = await fetch(`${GO_BASE}/sources`, { signal: AbortSignal.timeout(2500) })
      sources = (await sres.json()).sources || []
    } catch (_) { /* 忽略 */ }
    healthCache = { at: now, ok: true, version: json.version, sources }
  } catch (e) {
    healthCache = { at: now, ok: false, sources: [], error: e.message }
  }
  return healthCache
}

function publicBase(req) {
  const proto = req.headers['x-forwarded-proto'] || req.protocol || 'http'
  return `${proto}://${req.headers.host}/multi`
}

async function callGo(pathname, { query, req, method = 'GET', body, timeoutMs = TIMEOUT_MS } = {}) {
  const qs = query ? '?' + new URLSearchParams(query).toString() : ''
  const headers = { 'X-Public-Base': req ? publicBase(req) : `${GO_BASE}/multi` }
  const res = await fetch(`${GO_BASE}${pathname}${qs}`, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(timeoutMs),
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch (_) { /* 非 JSON */ }
  return { status: res.status, json, text }
}

// ---------- 汽水试听兜底 ----------
const sodaMatcher = createMatcher({
  callGo,
  log: (msg) => console.log('[soda-match]', msg),
})

/** 匹配最多等这么久，超时先放汽水自己的（后台继续匹配，下次直接命中） */
const MATCH_BUDGET_MS = Number(process.env.SODA_MATCH_BUDGET || 3000)

/** 已解析好的完整版地址短期复用，避免同一首歌反复取址 */
const URL_CACHE_TTL = 5 * 60 * 1000
const urlCache = new Map() // sodaId → { at, url, proxied, matched }

function withTimeout(promise, ms) {
  return Promise.race([
    promise,
    new Promise((resolve) => {
      const t = setTimeout(() => resolve(null), ms)
      if (t.unref) t.unref()
    }),
  ])
}

/**
 * 汽水歌曲取址。汽水对未登录用户的 VIP 曲目只给 30~60 秒预览流，
 * 因此先用「歌名 + 歌手 + 时长」在其它音源找完整版，命中就用它；
 * 找不到 / 超时 / 解析失败一律回落到汽水自己的流（行为与之前一致）。
 */
async function resolveSodaFullUrl(token, req) {
  const song = decodeSongToken(token)
  if (!song || song.source !== 'soda' || !song.name) return null

  const cands = await withTimeout(sodaMatcher.findMatch(song, req), MATCH_BUDGET_MS).catch(() => null)
  if (!cands || !cands.length) return null

  // 多个候选同时取址 + 试听，按分数顺序认领第一个能播的（并发起来总耗时≈最慢的那条）
  const tasks = cands.slice(0, 2).map(async (cand) => {
    const matched = {
      source: cand.source,
      name: cand.name,
      artist: cand.artist,
      duration: cand.duration,
    }
    try {
      const { json } = await callGo('/song/url', { query: { s: cand.token }, req })
      // 1) 直链能播就直接给，最省带宽
      if (json && json.url && (await isPlayableUrl(json.url))) {
        return { url: json.url, proxied: !!json.proxied, matched }
      }
      // 2) 直链放不出声（有的源会返回几十字节的错误体）→ 改走我们自己的回源，
      //    服务端重新解析并透传 Range，客户端拿到的仍是同一个 token
      const proxiedUrl = `${publicBase(req)}/stream?s=${encodeURIComponent(cand.token)}`
      const internalProbe = `${GO_BASE}/stream?s=${encodeURIComponent(cand.token)}`
      if (await isPlayableUrl(internalProbe)) {
        return { url: proxiedUrl, proxied: true, matched }
      }
      // 3) 两条都不行才放弃这条候选
      sodaMatcher.markBad(song.id, cand.token)
      console.log('[soda-match] 跳过坏链接', cand.source, cand.name, String(json && json.url || '').slice(0, 60))
      return null
    } catch (_) {
      return null
    }
  })

  for (const task of tasks) {
    const hit = await task
    if (hit) return hit
  }
  return null
}

/** 试拉 1KB：能拿到 200/206 且体积像样才算能播 */
async function isPlayableUrl(url) {
  try {
    const res = await fetch(url, {
      headers: { Range: 'bytes=0-1023', 'User-Agent': 'qMusic' },
      signal: AbortSignal.timeout(3000),
    })
    let size = 0
    const range = res.headers.get('content-range') || ''
    if (range.includes('/')) size = Number(range.split('/').pop()) || 0
    if (!size) size = Number(res.headers.get('content-length')) || 0
    try {
      res.body && res.body.cancel()
    } catch (_) {}
    const okStatus = res.status === 200 || res.status === 206
    return okStatus && size >= 20 * 1024
  } catch (_) {
    return false
  }
}

/** 统一的上游错误处理：不可达 → 503，业务错误 → 502 */
function upstreamFail(res, e) {
  const msg = String((e && e.message) || e)
  const offline = /fetch failed|ECONNREFUSED|timeout|aborted|ENOTFOUND/i.test(msg)
  res.status(offline ? 503 : 502).json({
    ok: false,
    error: offline ? '多音源服务未启动（musiclib-service）' : msg,
    hint: offline ? `请先启动 Go 服务：server/musiclib-service/qmusic-musiclib.exe（默认 ${GO_BASE}）` : undefined,
  })
}

// ---------- 路由 ----------

router.get('/sources', async (req, res) => {
  const h = await probe(true)
  res.json({
    ok: h.ok,
    available: h.ok,
    upstream: GO_BASE,
    version: h.version || null,
    sources: h.sources || [],
    error: h.ok ? undefined : h.error,
  })
})

router.get('/search', async (req, res) => {
  const source = req.query.source || 'netease'
  const keyword = String(req.query.keyword || req.query.keywords || '').trim()
  if (!keyword) return res.status(400).json({ ok: false, error: 'keyword is required' })
  const limit = Number(req.query.limit || 30)
  try {
    const { status, json, text } = await callGo('/search', { query: { source, keyword, limit }, req })
    if (!json) return res.status(status).json({ ok: false, error: text.slice(0, 200) })
    res.status(json.ok === false ? 502 : 200).json(json)
  } catch (e) { upstreamFail(res, e) }
})

router.get('/song/url', async (req, res) => {
  const token = req.query.s || req.query.token
  if (!token) return res.status(400).json({ ok: false, error: 'missing s (song token)' })

  // 汽水 VIP 曲目：优先播其它音源的完整版（?original=1 可强制用汽水原流，便于排查）
  if (req.query.original !== '1') {
    const sodaSong = decodeSongToken(token)
    const cached = sodaSong && sodaSong.source === 'soda' ? urlCache.get(String(sodaSong.id)) : null
    if (cached && Date.now() - cached.at < URL_CACHE_TTL) {
      return res.status(200).json({
        ok: true,
        url: cached.url,
        proxied: cached.proxied,
        matched: cached.matched,
        note: 'soda-preview-replaced',
      })
    }

    const alt = await withTimeout(resolveSodaFullUrl(token, req), MATCH_BUDGET_MS + 1500).catch(() => null)
    if (alt && sodaSong) {
      urlCache.set(String(sodaSong.id), { at: Date.now(), ...alt })
      if (urlCache.size > 500) urlCache.delete(urlCache.keys().next().value)
    }
    if (alt) {
      return res.status(200).json({
        ok: true,
        url: alt.url,
        proxied: alt.proxied,
        matched: alt.matched,
        note: 'soda-preview-replaced',
      })
    }
  }

  try {
    const { status, json, text } = await callGo('/song/url', { query: { s: token }, req })
    if (!json) return res.status(status).json({ ok: false, error: text.slice(0, 200) })
    res.status(200).json(json)
  } catch (e) { upstreamFail(res, e) }
})

router.get('/lyric', async (req, res) => {
  const token = req.query.s || req.query.token
  if (!token) return res.status(400).json({ ok: false, error: 'missing s (song token)' })
  try {
    const { status, json, text } = await callGo('/lyric', { query: { s: token }, req })
    if (!json) return res.status(status).json({ ok: false, error: text.slice(0, 200) })
    res.status(200).json(json)
  } catch (e) { upstreamFail(res, e) }
})

router.get('/playlist/search', async (req, res) => {
  const source = req.query.source || 'netease'
  const keyword = String(req.query.keyword || '').trim()
  if (!keyword) return res.status(400).json({ ok: false, error: 'keyword is required' })
  try {
    const { status, json, text } = await callGo('/playlist/search', { query: { source, keyword }, req })
    if (!json) return res.status(status).json({ ok: false, error: text.slice(0, 200) })
    res.status(200).json(json)
  } catch (e) { upstreamFail(res, e) }
})

router.get('/playlist/songs', async (req, res) => {
  const source = req.query.source || 'netease'
  const id = String(req.query.id || '').trim()
  if (!id) return res.status(400).json({ ok: false, error: 'id is required' })
  try {
    const { status, json, text } = await callGo('/playlist/songs', { query: { source, id }, req })
    if (!json) return res.status(status).json({ ok: false, error: text.slice(0, 200) })
    res.status(200).json(json)
  } catch (e) { upstreamFail(res, e) }
})

// 音频回源：透传 Range，支持拖动进度
router.get('/stream', async (req, res) => {
  const token = req.query.s
  if (!token) return res.status(400).end()
  try {
    const upstream = await fetch(`${GO_BASE}/stream?s=${encodeURIComponent(token)}`, {
      headers: { Range: req.headers.range || '', 'User-Agent': req.headers['user-agent'] || 'qMusic' },
    })
    res.status(upstream.status)
    for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges', 'cache-control']) {
      const v = upstream.headers.get(h)
      if (v) res.setHeader(h, v)
    }
    if (!upstream.body) return res.end()
    Readable.fromWeb(upstream.body).pipe(res)
  } catch (e) {
    res.status(502).json({ ok: false, error: String(e.message || e) })
  }
})

module.exports = { router, probe, GO_BASE }
