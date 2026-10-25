/**
 * 汽水 VIP 试听兜底：汽水未登录账号对 VIP 曲目只给 30~60 秒的预览流，
 * 客户端表现为「歌播到一半就跳下一首」。这里按「歌名 + 歌手 + 时长」在其它音源
 * 找同一首歌的完整版，命中就优先播它（找不到仍然回落到汽水的预览流）。
 *
 * 只做一件事：输入汽水歌曲元信息 → 输出可用于取址的候选 token。
 */
const fs = require('fs')
const path = require('path')

const CACHE_FILE = path.join(__dirname, '.cache', 'soda-match.json')
const MATCH_TTL = 6 * 60 * 60 * 1000 // 匹配结果 6 小时内复用；URL 本身短命，每次都重新取
const MISS_TTL = 6 * 60 * 60 * 1000 // 没匹配上的也缓存，避免每次都白跑三轮搜索
const SEARCH_TIMEOUT = 10000

/** 参与补全的音源，按命中率排序 */
const MATCH_SOURCES = (process.env.SODA_MATCH_SOURCES || 'netease,qq,kugou,kuwo')
  .split(',')
  .map((s) => s.trim())
  .filter(Boolean)

const MIN_SCORE = 60

/** 缓存结构版本：改了字段就加一，旧缓存自动作废 */
const CACHE_VERSION = 2

/** base64url(JSON) —— 与 Go 端 encodeSong 一致 */
function decodeSongToken(token) {
  if (!token || typeof token !== 'string') return null
  try {
    const json = Buffer.from(token, 'base64url').toString('utf8')
    const song = JSON.parse(json)
    if (!song || typeof song !== 'object' || !song.source) return null
    return song
  } catch (_) {
    return null
  }
}

function cleanText(input) {
  return String(input || '')
    .toLowerCase()
    .replace(/\([^)]*\)|（[^）]*）|\[[^\]]*\]|【[^】]*】/g, ' ')
    .replace(/[\s\-_·・.,'"!?~、:：/]/g, '')
}

function artistList(input) {
  return String(input || '')
    .toLowerCase()
    .split(/[/,、&;]+|\s+feat\.?\s+/)
    .map((s) => cleanText(s))
    .filter(Boolean)
}

/** 打分：-1 表示歌名完全不沾边，直接淘汰 */
function scoreCandidate(target, cand) {
  const t = cleanText(target.name)
  const ct = cleanText(cand.name)
  if (!t || !ct) return -1

  let score = 0
  if (t === ct) score += 40
  else if (ct.includes(t) || t.includes(ct)) score += 20
  else return -1

  const ta = artistList(target.artist)
  const ca = artistList(cand.artist)
  if (ta.length && ca.length && ta.some((x) => ca.some((y) => y === x || y.includes(x) || x.includes(y)))) {
    score += 25
  }

  const diff = Math.abs(Number(cand.duration || 0) - Number(target.duration || 0))
  if (diff <= 3) score += 25
  else if (diff <= 10) score += 15
  else if (diff > 25) score -= 30

  if (target.album && cand.album && cleanText(cand.album) === cleanText(target.album)) score += 10
  return score
}

function createMatcher({ callGo, log = () => {} }) {
  /** id → { at, hit, cand, reason } */
  const cache = new Map()
  const inflight = new Map()
  let loaded = false
  let saveTimer = null

  function load() {
    if (loaded) return
    loaded = true
    try {
      const raw = JSON.parse(fs.readFileSync(CACHE_FILE, 'utf8'))
      const now = Date.now()
      // 旧版本的缓存结构（单个 cand）直接丢弃，避免读到没有候选列表的历史条目
      if (raw && raw.__v === CACHE_VERSION) {
        for (const [id, entry] of Object.entries(raw.entries || {})) {
          if (entry && typeof entry.at === 'number' && now - entry.at < MATCH_TTL) cache.set(id, entry)
        }
      }
    } catch (_) {
      /* 没缓存文件是常态 */
    }
  }

  function save() {
    if (saveTimer) return
    saveTimer = setTimeout(() => {
      saveTimer = null
      try {
        fs.mkdirSync(path.dirname(CACHE_FILE), { recursive: true })
        const entries = {}
        for (const [id, entry] of cache) entries[id] = entry
        fs.writeFileSync(CACHE_FILE, JSON.stringify({ __v: CACHE_VERSION, entries }))
      } catch (_) {
        /* 写不进就当没有 */
      }
    }, 3000)
    if (saveTimer.unref) saveTimer.unref()
  }

  async function search(source, keyword, req) {
    try {
      const { json } = await callGo('/search', {
        query: { source, keyword, limit: 10 },
        req,
        timeoutMs: SEARCH_TIMEOUT,
      })
      if (!json) return { songs: [], ok: false }
      return { songs: json.songs || [], ok: true }
    } catch (e) {
      log(`搜索失败 ${source}: ${e.message}`)
      return { songs: [], ok: false }
    }
  }

  function firstArtist(song) {
    return String(song.artist || '').split(/[/,、]/)[0].trim()
  }

  /** 一轮多音源并发搜索，返回按分数排序的候选（多存几个用于失败重试） */
  async function bestOf(keyword, target, req) {
    const batches = await Promise.all(MATCH_SOURCES.map((src) => search(src, keyword, req)))
    const ranked = []
    let failed = 0
    batches.forEach((batch, i) => {
      const src = MATCH_SOURCES[i]
      if (!batch.ok) failed += 1
      for (const cand of batch.songs) {
        if (!cand || !cand.token) continue
        const score = scoreCandidate(target, cand)
        if (score >= MIN_SCORE) ranked.push({ ...cand, score, src })
      }
    })
    ranked.sort((a, b) => b.score - a.score)
    return { ranked: ranked.slice(0, 4), failed }
  }

  /**
   * 找同一首歌的完整版。
   * @returns {Promise<null | {token:string, source:string, name:string, artist:string, duration:number, score:number}>}
   */
  async function findMatch(song, req) {
    load()
    const key = String(song.id || '')
    if (!key) return null

    const hit = cache.get(key)
    if (hit) {
      const alive = (hit.candidates || []).filter((c) => !(hit.bad || []).includes(c.token))
      return alive.length ? alive : null
    }
    if (inflight.has(key)) return inflight.get(key)

    const task = (async () => {
      const artist = firstArtist(song)
      let result = await bestOf(`${song.name} ${artist}`.trim(), song, req)
      let ranked = result.ranked
      const failed = () => result.failed
      // 带歌手没搜到，退一步只搜歌名（翻唱/合辑有时歌手字段对不上）
      if (!ranked.length && song.name) {
        result = await bestOf(String(song.name), song, req)
        ranked = result.ranked
      }

      const candidates = ranked.map((c) => ({
        token: c.token,
        source: c.src,
        name: c.name,
        artist: c.artist,
        duration: Number(c.duration || 0),
        score: c.score,
      }))

      // 有音源搜索失败时结果不可信 —— 不写缓存，下次播放再试一遍
      if (!candidates.length && failed() > 0) {
        log(`汽水补全未果（${failed()} 个音源超时）：${song.name}`)
        return null
      }

      const entry = { at: Date.now(), candidates, bad: [] }
      cache.set(key, entry)
      save()
      if (candidates.length) {
        log(`汽水补全命中：${song.name} → ${candidates[0].source} ${candidates[0].name}（score ${candidates[0].score}）`)
      }
      return candidates.length ? candidates : null
    })().finally(() => inflight.delete(key))

    inflight.set(key, task)
    return task
  }

  /** 标记某个候选放不出声（CDN 报错 / 空文件），下次跳过它 */
  function markBad(songId, tokenKey) {
    const entry = cache.get(String(songId || ''))
    if (!entry) return
    if (!Array.isArray(entry.bad)) entry.bad = []
    if (!entry.bad.includes(tokenKey)) entry.bad.push(tokenKey)
    save()
  }

  return { findMatch, markBad, decodeSongToken, cache }
}

module.exports = { createMatcher, decodeSongToken, scoreCandidate, cleanText }
