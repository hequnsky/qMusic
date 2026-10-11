// 探测歌词接口：搜索歌曲 → 取歌词 → 打印时间戳
const BASE = 'http://127.0.0.1:3000'

async function j(path) {
  const r = await fetch(BASE + path, { signal: AbortSignal.timeout(8000) })
  return { status: r.status, body: await r.text() }
}

;(async () => {
  const kw = process.argv[2] || 'Whatcha Reckon'
  const s = await j(`/search?keywords=${encodeURIComponent(kw)}&limit=5`)
  console.log('search status', s.status)
  let parsed
  try { parsed = JSON.parse(s.body) } catch (e) { console.log(s.body.slice(0, 300)); return }
  const songs = parsed.result?.songs || parsed.data?.songs || parsed.songs || []
  console.log('songs:', songs.length)
  for (const so of songs.slice(0, 3)) {
    console.log(' -', so.id, so.name, (so.artists || so.ar || []).map(a => a.name).join('/'))
  }
  if (!songs.length) { console.log(JSON.stringify(parsed).slice(0, 500)); return }
  const id = songs[0].id
  const l = await j(`/lyric?id=${id}`)
  console.log('lyric status', l.status, 'len', l.body.length)
  try {
    const lj = JSON.parse(l.body)
    const lrc = lj.lrc?.lyric || lj.data?.lyric || ''
    console.log('lrc first 400 chars:')
    console.log(lrc.slice(0, 400))
    const re = /\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]/g
    let m, n = 0
    while ((m = re.exec(lrc)) && n < 12) {
      console.log('  ts', m[1] + ':' + m[2] + '.' + (m[3] || '0'))
      n++
    }
  } catch (e) { console.log(l.body.slice(0, 400)) }
})()
