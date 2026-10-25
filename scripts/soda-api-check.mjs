// 验证新增的 /soda/playlists、/soda/playlist/:id、/soda/radios
const base = 'http://127.0.0.1:3300'
const get = async (p) => {
  const t0 = Date.now()
  const r = await fetch(base + p, { signal: AbortSignal.timeout(120000) })
  const j = await r.json()
  return { status: r.status, j, ms: Date.now() - t0 }
}

const r1 = await get('/soda/playlists?limit=24')
console.log('playlists:', r1.status, r1.ms + 'ms', 'total=', r1.j.total, 'len=', (r1.j.list || []).length)
console.log((r1.j.list || []).slice(0, 8).map((p) => `${p.name}(${p.trackCount})`).join('\n'))
console.log('sample:', JSON.stringify(r1.j.list && r1.j.list[0]))

const first = r1.j.list && r1.j.list[0]
if (first) {
  const r2 = await get(`/soda/playlist/${first.id}?limit=60`)
  console.log('\nplaylist detail:', r2.status, r2.ms + 'ms', 'songs=', (r2.j.songs || []).length)
  console.log('meta:', JSON.stringify(r2.j.playlist))
  console.log('song0:', JSON.stringify(r2.j.songs && r2.j.songs[0]))
}

const r3 = await get('/soda/radios')
console.log('\nradios:', r3.status, (r3.j.list || []).length, (r3.j.list || []).slice(0, 5).map((x) => x.name).join(' | '))
console.log('radio sample:', JSON.stringify(r3.j.list && r3.j.list[0]))

if (r3.j.list && r3.j.list[0]) {
  const r4 = await get(`/soda/radio/${r3.j.list[0].id}?limit=24`)
  console.log('\nradio tracks:', r4.status, (r4.j.songs || []).length, r4.ms + 'ms')
  console.log((r4.j.songs || []).slice(0, 5).map((s) => `${s.name} - ${s.artist}`).join('\n'))
}
