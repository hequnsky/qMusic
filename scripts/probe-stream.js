// 诊断播放卡顿：搜索一首歌 -> 取播放地址 -> 试拉音频流
const BASE = 'http://127.0.0.1:3000';

async function j(url) {
  const r = await fetch(url, { signal: AbortSignal.timeout(8000) });
  return r.json();
}

(async () => {
  const kw = process.argv[2] || 'Country Nation';
  console.log('== search:', kw);

  let id;
  try {
    const s = await j(`${BASE}/search?keywords=${encodeURIComponent(kw)}&limit=1`);
    const song = s?.result?.songs?.[0];
    if (!song) return console.log('  no result:', JSON.stringify(s).slice(0, 200));
    id = song.id;
    console.log('  found:', song.name, '/', (song.artists || song.ar || []).map(a => a.name).join(','), 'id=', id);
  } catch (e) {
    return console.log('  search FAILED:', e.message);
  }

  let playUrl;
  try {
    const u = await j(`${BASE}/song/url/v1?id=${id}&level=standard`);
    const d = u?.data?.[0];
    playUrl = d?.url;
    console.log('== song url:', playUrl ? playUrl.slice(0, 120) + '...' : '(null)', '| fee=', d?.fee, '| br=', d?.br);
    if (!playUrl) return console.log('  上游没给可播放地址（可能版权/试听受限）');
  } catch (e) {
    return console.log('  song/url FAILED:', e.message);
  }

  // 试拉流：只读前 64KB，看多久能到
  const t0 = Date.now();
  try {
    const r = await fetch(playUrl, {
      headers: { Range: 'bytes=0-65535' },
      signal: AbortSignal.timeout(15000),
    });
    const buf = await r.arrayBuffer();
    console.log(`== stream: HTTP ${r.status}, ${buf.byteLength} bytes in ${Date.now() - t0}ms`,
      '| type=', r.headers.get('content-type'), '| len=', r.headers.get('content-length'));
  } catch (e) {
    console.log(`== stream FAILED after ${Date.now() - t0}ms:`, e.name, e.message);
  }
})();
