'use strict'

/**
 * 汽水音乐（Soda / 抖音音乐）榜单接入。
 *
 * 数据来源：开源项目 github.com/guohuiyuan/music-lib 的 soda/toplist.go。
 * 其实现要点（照抄，不自行猜测）：
 *   · PC 榜单接口 https://api.qishui.com/luna/pc/charts/{chart_id}
 *     —— 无需登录、无需 a_bogus 签名，但**必须**带 luna_pc 的 query 参数与 x-luna-* 头，
 *        否则返回 ERR_INVALID_PARAM；
 *   · 备用接口 https://api5-lf.qishui.com/luna/charts?id=&charge=0（PC 失败时回退）；
 *   · 汽水没有公开的「榜单目录」接口，官方四个榜的 ID 是内置常量。
 *
 * 本层职责：把汽水的榜单结构转换成与 /multi/* 完全一致的 Song DTO（含 token），
 * 这样 Android 端不需要任何新播放逻辑 —— 拿到的就是普通多音源歌曲，
 * 播放走 /multi/song/url?s=token → /multi/stream。
 */

/**
 * ⚠️ 真实榜单 ID 都是 19 位数字（如 7036274230471712007），超过
 * Number.MAX_SAFE_INTEGER，直接当 JSON 数字下发会被 JS 截断成 ...2000，
 * 客户端拿去做参数就再也查不到。因此对外统一暴露 appId（900001 起的小整数），
 * 真实 ID 只在服务端内部使用。
 */
const CHARTS = [
  {
    appId: 900001,
    id: '7036274230471712007',
    name: '热歌榜',
    description: '汽水音乐内每周热度最高的 50 首歌，每周四更新',
    cover:
      'https://p3-luna.douyinpic.com/img/tos-cn-i-b829550vbb/d0d8d48461a62748e84689cdf049b19a.png~tplv-b829550vbb-resize:960:960.png',
    trackCount: 50,
  },
  {
    appId: 900002,
    id: '7060812597884869927',
    name: '新歌榜',
    description: '近期发行的热度最高的 50 首新歌，每周四更新',
    cover:
      'https://p3-luna.douyinpic.com/img/tos-cn-i-b829550vbb/f12f7eb5b54d0899c7c724df009668a8.png~tplv-b829550vbb-resize:960:960.png',
    trackCount: 50,
  },
  {
    appId: 900003,
    id: '7061475546400005410',
    name: '欧美榜',
    description: '汽水音乐内每周热度最高的 50 首外文歌曲，每周四更新',
    cover:
      'https://p3-luna.douyinpic.com/img/tos-cn-i-b829550vbb/33747550ed5499b58feda42a21748637.png~tplv-b829550vbb-resize:960:960.png',
    trackCount: 50,
  },
  {
    appId: 900004,
    id: '7415959718721494311',
    name: '音乐人歌曲榜',
    description: '抖音音乐人开放平台上传歌曲，综合每周站内热度排序展示',
    cover:
      'https://p3-luna.douyinpic.com/img/tos-cn-v-2774c002/o8FQKiQQBxHWa2hzsBNAgYOX6iEHEAibADAbfB~tplv-b829550vbb-resize:960:960.png',
    trackCount: 25,
  },
]

/** appId(900001…) 或真实 19 位 chart id 都能查到定义 */
function findChart(idOrAppId) {
  const key = String(idOrAppId || '').trim()
  return CHARTS.find((c) => String(c.appId) === key || c.id === key) || null
}

const PC_API = 'https://api.qishui.com/luna/pc/charts'
const FALLBACK_API = 'https://api5-lf.qishui.com/luna/charts'
const UA_PC_APP = 'LunaPC/3.3.0(359450208)'
const TIMEOUT_MS = 12000
const CACHE_TTL = 10 * 60 * 1000 // 榜单每周四更新，10 分钟缓存足够，也防止用户狂刷新

/** PC 端必需参数（music-lib: sodaPCAppParams） */
function pcParams() {
  const now = Date.now()
  return new URLSearchParams({
    aid: '386088',
    app_name: 'luna_pc',
    region: 'cn',
    geo_region: 'cn',
    os_region: 'cn',
    sim_region: '',
    device_id: String(now),
    cdid: '',
    iid: String(now + 1),
    version_name: '3.3.0',
    version_code: '30030000',
    channel: 'official',
    build_mode: 'master',
    network_carrier: '',
    ac: 'wifi',
    tz_name: 'Asia/Shanghai',
    resolution: '',
    device_platform: 'windows',
    device_type: 'Windows',
    os_version: 'Windows 11',
    fp: String(now),
  })
}

const PC_HEADERS = {
  'User-Agent': UA_PC_APP,
  'x-luna-background-type': 'foreground',
  'x-luna-is-background-req': '0',
  'x-luna-is-local-user': '1',
}

async function getJSON(url, headers) {
  const res = await fetch(url, { headers, signal: AbortSignal.timeout(TIMEOUT_MS) })
  const text = await res.text()
  if (!res.ok) throw new Error(`HTTP ${res.status}: ${text.slice(0, 120)}`)
  try {
    return JSON.parse(text)
  } catch (_) {
    throw new Error(`非 JSON 响应: ${text.slice(0, 120)}`)
  }
}

/** 封面拼接：urls[0] + uri + "~" + template_prefix + "-resize:960:960.png" */
function coverOf(img) {
  if (!img) return null
  const uri = img.uri || ''
  if (!uri) return (img.urls && img.urls[0]) || null
  if (/^https?:\/\//.test(uri)) return uri
  const base = (img.urls && img.urls[0]) || 'https://p3-luna.douyinpic.com/img/'
  const prefix = img.template_prefix || 'tplv-b829550vbb'
  const sep = base.endsWith('/') ? '' : '/'
  return `${base}${sep}${uri}~${prefix}-resize:960:960.png`
}

/** 取一个尽量高（但不一定无损）的音质档，写进 token 里的 size/bitrate */
function pickBitRate(bitRates) {
  const list = Array.isArray(bitRates) ? bitRates : []
  const byQuality = (q) => list.find((b) => b.quality === q)
  return byQuality('highest') || byQuality('higher') || byQuality('medium') || list[0] || null
}

/**
 * token 的载荷与 Go 端 model.Song 完全对齐（base64url JSON）。
 * Go 侧只要拿到 id + source=soda 就能解析下载地址并解密，
 * 其余字段用于列表展示与收藏/历史回填。
 */
function encodeToken(song) {
  const payload = {
    id: song.id,
    name: song.name,
    artist: song.artist,
    album: song.album,
    album_id: song.albumId || '',
    duration: song.duration,
    size: song.size || 0,
    bitrate: song.bitrate || 0,
    source: 'soda',
    url: '',
    ext: 'mp3',
    cover: song.cover || '',
    link: song.link || '',
    extra: {
      track_id: song.id,
      album_id: song.albumId || '',
      is_vip: song.isVip ? 'true' : 'false',
    },
  }
  return Buffer.from(JSON.stringify(payload), 'utf8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '')
}

/** 汽水 track → 统一 Song DTO */
function toSongDTO(track, rank) {
  const artists = (track.artists || []).map((a) => a && a.name).filter(Boolean)
  const album = track.album || {}
  const br = pickBitRate(track.bit_rates)
  const duration = Math.round((track.duration || 0) / 1000)
  const label = track.label_info || {}
  const song = {
    id: String(track.id || ''),
    name: track.name || '',
    artist: artists.join(' / '),
    album: album.name || '',
    albumId: album.id || '',
    duration,
    size: br ? br.size : 0,
    bitrate: br ? Math.round((br.br || 0) / 1000) : 0,
    cover: coverOf(album.url_cover),
    link: `https://www.qishui.com/track/${track.id}`,
    isVip: label.only_vip_playable === true || label.only_vip_download === true,
    rank,
  }
  return {
    id: song.id,
    name: song.name,
    artist: song.artist,
    album: song.album,
    duration: song.duration,
    cover: song.cover,
    source: 'soda',
    ext: 'mp3',
    link: song.link,
    token: encodeToken(song),
  }
}

const cache = new Map() // chartId → { at, payload }

/**
 * 拉取指定榜单（列表按名次排序）。
 * @param {string} chartId
 * @param {{ force?: boolean }} opts
 */
async function fetchChart(idOrAppId, opts = {}) {
  const meta = findChart(idOrAppId)
  if (!meta) {
    const e = new Error(`unknown soda chart: ${idOrAppId}`)
    e.code = 'UNKNOWN_CHART'
    throw e
  }
  const chartId = meta.id

  const hit = cache.get(chartId)
  if (!opts.force && hit && Date.now() - hit.at < CACHE_TTL) return hit.payload

  let chart = null
  let lastErr = null

  // 1) PC 接口
  try {
    const json = await getJSON(`${PC_API}/${chartId}?${pcParams()}`, PC_HEADERS)
    if (json && json.chart && Array.isArray(json.chart.track_ranks)) chart = json.chart
    else throw new Error('响应缺少 chart.track_ranks')
  } catch (e) {
    lastErr = e
  }

  // 2) 移动端回退
  if (!chart) {
    try {
      const json = await getJSON(
        `${FALLBACK_API}?id=${encodeURIComponent(chartId)}&charge=0`,
        { 'User-Agent': PC_HEADERS['User-Agent'] },
      )
      if (json && json.chart && Array.isArray(json.chart.track_ranks)) chart = json.chart
      else throw new Error('回退响应缺少 chart.track_ranks')
      lastErr = null
    } catch (e) {
      lastErr = lastErr || e
    }
  }

  if (!chart) {
    if (hit) return hit.payload // 有旧缓存就先用着，别让首页空掉
    const e = new Error(`汽水榜单拉取失败：${(lastErr && lastErr.message) || '未知错误'}`)
    e.code = 'UPSTREAM_FAIL'
    throw e
  }

  const rankings = []
  let index = 0
  for (const item of chart.track_ranks) {
    index += 1
    const track = item && item.track
    if (!track || !track.id) continue
    const rank = item.rank || {}
    rankings.push({
      song: toSongDTO(track, index),
      rank: index,
      rankType: rank.rank_type || '',
      rankDelta: Number(rank.rank_delta || 0),
    })
  }

  const payload = {
    ok: true,
    source: 'soda',
    chart: {
      id: meta.appId,
      chartId: meta.id,
      name: chart.title || meta.name,
      description: meta.description,
      coverImgUrl: meta.cover,
      updateFrequency: '每周四更新',
      trackCount: chart.count_tracks || rankings.length,
      period: chart.period || 'weekly',
      releaseTime: chart.release_time || 0,
    },
    songs: rankings.map((r) => r.song),
    rankings: rankings.map((r) => ({ rank: r.rank, rankType: r.rankType, rankDelta: r.rankDelta })),
    cachedAt: Date.now(),
  }
  cache.set(chartId, { at: Date.now(), payload })
  return payload
}

/** 榜单目录（静态，无需网络） */
function listCharts() {
  return CHARTS.map((c) => ({
    // 注意：这里必须是安全范围内的整数，19 位真实 ID 会丢精度
    id: c.appId,
    chartId: c.id,
    name: c.name,
    coverImgUrl: c.cover,
    description: c.description,
    updateFrequency: '每周四更新',
    trackCount: c.trackCount,
    source: 'soda',
  }))
}

// ---------- 路由 ----------

const express = require('express')
const router = express.Router()

router.get('/toplist', (req, res) => {
  res.json({ ok: true, source: 'soda', list: listCharts() })
})

router.get('/toplist/:id', async (req, res) => {
  const id = String(req.params.id || '').trim()
  if (!/^\d+$/.test(id)) {
    return res.status(400).json({ ok: false, error: 'invalid soda chart id' })
  }
  const limit = Math.max(0, Number(req.query.limit || 0))
  try {
    const payload = await fetchChart(id, { force: req.query.force === '1' })
    if (limit > 0) {
      // ⚠️ 必须复制出新的数组：payload 是缓存对象本身，
      // 直接 slice 赋值会把缓存里的完整榜单永久裁短（踩过）。
      return res.json({
        ...payload,
        songs: payload.songs.slice(0, limit),
        rankings: payload.rankings.slice(0, limit),
      })
    }
    res.json(payload)
  } catch (e) {
    res.status(e.code === 'UNKNOWN_CHART' ? 404 : 502).json({
      ok: false,
      error: String(e.message || e),
    })
  }
})

/** 首页推荐区用：热歌榜前 N 首（默认 12） */
router.get('/hot', async (req, res) => {
  const limit = Math.min(50, Math.max(1, Number(req.query.limit || 12)))
  try {
    const payload = await fetchChart('7036274230471712007', { force: req.query.force === '1' })
    res.json({
      ok: true,
      source: 'soda',
      chart: payload.chart,
      songs: payload.songs.slice(0, limit),
      rankings: payload.rankings.slice(0, limit),
    })
  } catch (e) {
    res.status(502).json({ ok: false, error: String(e.message || e) })
  }
})

// toSongDTO / encodeToken / coverOf 也被 soda-discover.js 复用：
// 歌单和榜单拿到的 track 结构完全一致，转换逻辑没必要写两遍。
module.exports = { CHARTS, fetchChart, listCharts, router, toSongDTO, encodeToken, coverOf }
