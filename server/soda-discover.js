'use strict'

/**
 * 汽水音乐「发现」数据：推荐歌单池 + 歌单详情 + 电台。
 *
 * 与 soda-chart.js 的区别：那边是官方四个榜单（固定 chart id），
 * 这边走汽水 App 的发现流接口，能拿到**成百上千个精选歌单**：
 *
 *   POST https://api.qishui.com/luna/discover/mix   { count }
 *        → inner_block[].resources[].entity.playlist（每次约 8~10 个，且每次返回新的）
 *   POST https://api.qishui.com/luna/discover       { first_request: true }
 *        → blocks[].inner_block[].resources[]（type=radio 的电台）
 *   POST https://api.qishui.com/luna/playlist/detail { playlist_id, count, cursor }
 *        → media_resources[].entity.track_wrapper.track（含 next_cursor 可翻页）
 *   POST https://api.qishui.com/luna/feed/radio/tracks { radio_id, count }
 *        → items[].entity.track_wrapper.track
 *
 * 不需要登录、不需要签名，只要 App 的 UA（见 ANDROID_HEADERS）。
 * ⚠️ 这些接口**没有** cursor：discover/mix 每次返回一批新的，
 * 所以「歌单池」是靠进程内多次调用累积去重得到的，不能按页取。
 */

const { encodeToken, toSongDTO, coverOf } = require('./soda-chart')

const ANDROID_HEADERS = {
  'User-Agent': 'Luna/19.1.0 Android',
  'Content-Type': 'application/json; charset=utf-8',
}
const LUNA = 'https://api.qishui.com/luna'
const TIMEOUT_MS = 15000

const POOL_TTL = 30 * 60 * 1000 // 歌单池缓存：30 分钟内复用，够新又不至于每次都打十几次上游
const DETAIL_TTL = 10 * 60 * 1000
const POOL_TARGET = 120 // 累积到这么多就停，太多了启动慢

async function postLuna(path, body) {
  const res = await fetch(`${LUNA}${path}`, {
    method: 'POST',
    headers: ANDROID_HEADERS,
    body: JSON.stringify(body),
    signal: AbortSignal.timeout(TIMEOUT_MS),
  })
  const text = await res.text()
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 120)}`)
  try {
    return JSON.parse(text)
  } catch (_) {
    throw new Error(`非 JSON 响应: ${text.slice(0, 120)}`)
  }
}

/** 汽水 playlist 对象 → 客户端歌单 DTO */
function toPlaylistDTO(pl) {
  const stats = pl.stats || {}
  const count = Number(pl.count_tracks || (pl.resource_cnt && pl.resource_cnt.track_cnt) || 0)
  return {
    // ⚠️ 19 位 id 必须以字符串下发：JSON 数字会丢精度（…1982 → …2000）
    id: String(pl.id || ''),
    name: pl.title || pl.public_title || '',
    coverUrl: coverOf(pl.url_cover),
    description: String(pl.desc || '').trim(),
    trackCount: count,
    playCount: Number(stats.count_played || 0),
    collectCount: Number(stats.count_collected || 0),
    creator: (pl.owner && (pl.owner.public_name || pl.owner.nickname)) || '',
    source: 'soda',
  }
}

/** 从 discover/mix 的 inner_block 里挖歌单 */
function playlistsOfMix(json) {
  const out = []
  for (const block of (json && json.inner_block) || []) {
    for (const res of block.resources || []) {
      const pl = res && res.entity && res.entity.playlist
      if (pl && pl.id) out.push(toPlaylistDTO(pl))
    }
  }
  return out
}

const pool = { at: 0, list: [] }

/**
 * 累积歌单池。每次 discover/mix 给 8~10 个且基本不重复，
 * 调 10 次能攒到 90 个左右；命中缓存直接返回。
 */
async function ensurePool(force = false) {
  if (!force && pool.list.length >= 24 && Date.now() - pool.at < POOL_TTL) return pool.list

  const seen = new Map(pool.list.map((p) => [p.id, p]))
  const rounds = force || seen.size < 24 ? 16 : 4
  let fail = 0

  for (let i = 0; i < rounds && seen.size < POOL_TARGET; i++) {
    try {
      const json = await postLuna('/discover/mix', { count: 30 })
      for (const pl of playlistsOfMix(json)) {
        if (pl.id && !seen.has(pl.id) && pl.trackCount > 0) seen.set(pl.id, pl)
      }
    } catch (e) {
      fail += 1
      if (seen.size === 0 && fail >= 2) throw e
    }
  }

  // 曲目太少的（1~5 首的碎片歌单）排后面，让首页先看到像样的
  const list = [...seen.values()].sort((a, b) => b.trackCount - a.trackCount)
  if (list.length > 0) {
    pool.list = list
    pool.at = Date.now()
  }
  return pool.list
}

/** 首页/更多页：从池子里按 offset 取 */
async function listPlaylists({ limit = 20, offset = 0, force = false } = {}) {
  const all = await ensurePool(force)
  const start = Math.max(0, offset)
  return {
    total: all.length,
    list: all.slice(start, start + Math.max(1, limit)),
  }
}

const detailCache = new Map() // playlistId → { at, payload }

/** 歌单详情：自动翻页到 limit 首 */
async function fetchPlaylistDetail(playlistId, { limit = 100, force = false } = {}) {
  const key = String(playlistId)
  const hit = detailCache.get(key)
  if (!force && hit && Date.now() - hit.at < DETAIL_TTL) {
    const cached = hit.payload
    return {
      ...cached,
      songs: cached.songs.slice(0, limit),
    }
  }

  const songs = []
  const seen = new Set()
  let cursor = ''
  let meta = null
  let guard = 0

  while (songs.length < limit && guard < 8) {
    guard += 1
    const body = { playlist_id: key, count: Math.min(100, Math.max(20, limit - songs.length)) }
    if (cursor) body.cursor = cursor
    const json = await postLuna('/playlist/detail', body)
    if (!meta && json.playlist) meta = json.playlist

    const resources = json.media_resources || []
    if (resources.length === 0) break
    for (const item of resources) {
      const wrapper = item && item.entity && item.entity.track_wrapper
      const track = wrapper && wrapper.track
      if (!track || !track.id || seen.has(String(track.id))) continue
      seen.add(String(track.id))
      songs.push(toSongDTO(track, songs.length + 1))
      if (songs.length >= limit) break
    }

    const next = json.next_cursor
    if (!next || next === cursor) break
    cursor = String(next)
  }

  if (songs.length === 0) {
    if (hit) return hit.payload
    const e = new Error('汽水歌单暂无可用歌曲')
    e.code = 'EMPTY_PLAYLIST'
    throw e
  }

  const info = meta ? toPlaylistDTO(meta) : null
  const payload = {
    ok: true,
    source: 'soda',
    playlist: {
      id: key,
      name: (info && info.name) || '汽水歌单',
      coverImgUrl: (info && info.coverUrl) || null,
      description: (info && info.description) || '',
      trackCount: (info && info.trackCount) || songs.length,
      playCount: (info && info.playCount) || 0,
      creator: (info && info.creator) || '',
    },
    songs,
    cachedAt: Date.now(),
  }
  detailCache.set(key, { at: Date.now(), payload })
  return payload
}

// ---------- 电台 ----------

let radioCache = { at: 0, list: [] }

function radiosOfDiscover(json) {
  const out = []
  for (const block of (json && json.blocks) || []) {
    for (const inner of block.inner_block || []) {
      for (const res of inner.resources || []) {
        if (!res || res.type !== 'radio' || !res.resource_id) continue
        const style = res.style || {}
        out.push({
          // 电台 id 也是 19 位，统一转字符串
          id: String(res.resource_id),
          name: style.title || '汽水电台',
          description: String(style.desc || '').trim(),
          coverUrl: coverOf((style.cover_url_list || [])[0]),
          source: 'soda',
        })
      }
    }
  }
  return out
}

async function listRadios(force = false) {
  if (!force && radioCache.list.length && Date.now() - radioCache.at < POOL_TTL) return radioCache.list
  const json = await postLuna('/discover', { first_request: true })
  const list = radiosOfDiscover(json)
  if (list.length) radioCache = { at: Date.now(), list }
  return radioCache.list
}

/** 电台是无限流，没有 cursor：多次调用累积去重 */
async function radioTracks(radioId, { limit = 30 } = {}) {
  const songs = []
  const seen = new Set()
  for (let round = 0; round < 6 && songs.length < limit; round++) {
    const json = await postLuna('/feed/radio/tracks', { radio_id: String(radioId), count: 20 })
    for (const item of json.items || []) {
      const wrapper = item && item.entity && item.entity.track_wrapper
      const track = wrapper && wrapper.track
      if (!track || !track.id || seen.has(String(track.id))) continue
      seen.add(String(track.id))
      songs.push(toSongDTO(track, songs.length + 1))
    }
    if (!json.has_more) break
  }
  return songs
}

// ---------- 路由 ----------

const express = require('express')
const router = express.Router()

router.get('/playlists', async (req, res) => {
  const limit = Math.min(120, Math.max(1, Number(req.query.limit || 20)))
  const offset = Math.max(0, Number(req.query.offset || 0))
  try {
    const { list, total } = await listPlaylists({
      limit,
      offset,
      force: req.query.force === '1',
    })
    res.json({ ok: true, source: 'soda', total, list })
  } catch (e) {
    res.status(502).json({ ok: false, error: String(e.message || e) })
  }
})

router.get('/playlist/:id', async (req, res) => {
  const id = String(req.params.id || '').trim()
  if (!/^\d+$/.test(id)) {
    return res.status(400).json({ ok: false, error: 'invalid soda playlist id' })
  }
  const limit = Math.min(300, Math.max(1, Number(req.query.limit || 100)))
  try {
    const payload = await fetchPlaylistDetail(id, { limit, force: req.query.force === '1' })
    res.json(payload)
  } catch (e) {
    res.status(e.code === 'EMPTY_PLAYLIST' ? 404 : 502).json({
      ok: false,
      error: String(e.message || e),
    })
  }
})

router.get('/radios', async (req, res) => {
  try {
    const list = await listRadios(req.query.force === '1')
    res.json({ ok: true, source: 'soda', list })
  } catch (e) {
    res.status(502).json({ ok: false, error: String(e.message || e) })
  }
})

router.get('/radio/:id', async (req, res) => {
  const id = String(req.params.id || '').trim()
  if (!/^\d+$/.test(id)) {
    return res.status(400).json({ ok: false, error: 'invalid soda radio id' })
  }
  const limit = Math.min(60, Math.max(1, Number(req.query.limit || 30)))
  try {
    const songs = await radioTracks(id, { limit })
    if (songs.length === 0) {
      return res.status(404).json({ ok: false, error: '电台暂无可用歌曲' })
    }
    res.json({
      ok: true,
      source: 'soda',
      playlist: {
        id,
        name: '汽水电台',
        coverImgUrl: songs[0].cover,
        trackCount: songs.length,
        playCount: 0,
      },
      songs,
    })
  } catch (e) {
    res.status(502).json({ ok: false, error: String(e.message || e) })
  }
})

module.exports = {
  router,
  listPlaylists,
  fetchPlaylistDetail,
  listRadios,
  radioTracks,
}
