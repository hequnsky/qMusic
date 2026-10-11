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

async function callGo(pathname, { query, req, method = 'GET', body } = {}) {
  const qs = query ? '?' + new URLSearchParams(query).toString() : ''
  const headers = { 'X-Public-Base': req ? publicBase(req) : `${GO_BASE}/multi` }
  const res = await fetch(`${GO_BASE}${pathname}${qs}`, {
    method,
    headers,
    body,
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  const text = await res.text()
  let json = null
  try { json = JSON.parse(text) } catch (_) { /* 非 JSON */ }
  return { status: res.status, json, text }
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
