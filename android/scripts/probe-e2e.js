// 端到端链路：网关搜索 -> 取播放地址 -> 取歌词
const GW = 'http://127.0.0.1:3000';
const kw = encodeURIComponent('晴天 周杰伦');

(async () => {
  // 1) 网关多音源搜索
  const r = await fetch(`${GW}/multi/search?source=kugou&keyword=${kw}&limit=5`, { signal: AbortSignal.timeout(20000) });
  const j = await r.json();
  console.log('search status', r.status, 'songs', (j.songs || []).length);
  if (!j.songs || !j.songs.length) { console.log('no songs'); return; }
  const s0 = j.songs[0];
  console.log('song0:', s0.name, '/', s0.artist, 'token.len=', (s0.token || '').length);

  // 2) 取播放地址
  const r2 = await fetch(`${GW}/multi/song/url?s=${encodeURIComponent(s0.token)}`, { signal: AbortSignal.timeout(25000) });
  const j2 = await r2.json();
  console.log('url status', r2.status, JSON.stringify(j2).slice(0, 300));

  // 3) 取歌词
  const r3 = await fetch(`${GW}/multi/lyric?s=${encodeURIComponent(s0.token)}`, { signal: AbortSignal.timeout(20000) });
  const j3 = await r3.json();
  const lyr = j3.lyric || j3.lrc || '';
  console.log('lyric status', r3.status, 'len=', lyr.length, 'head=', lyr.slice(0, 60).replace(/\n/g, '|'));
})();
