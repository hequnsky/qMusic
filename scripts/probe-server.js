// 一次性服务端健康探测：网关 3000 / 多音源 /multi/sources
const targets = [
  'http://127.0.0.1:3000/',
  'http://127.0.0.1:3000/multi/sources',
  'http://127.0.0.1:3000/search/hot/detail',
]

;(async () => {
  for (const url of targets) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(6000) })
      const text = await r.text()
      console.log(`[OK] ${url} -> ${r.status}, ${text.length} bytes`)
      console.log('     ' + text.slice(0, 160).replace(/\s+/g, ' '))
    } catch (e) {
      console.log(`[FAIL] ${url} -> ${e.message}`)
    }
  }
})()
