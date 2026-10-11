// 验证 /multi/stream 返回真实音频字节 + Range 支持
const GW = 'http://127.0.0.1:3000';
const kw = encodeURIComponent('晴天 周杰伦');
(async () => {
  const j = await (await fetch(`${GW}/multi/search?source=kugou&keyword=${kw}&limit=3`, { signal: AbortSignal.timeout(20000) })).json();
  const token = j.songs[0].token;
  const u = await (await fetch(`${GW}/multi/song/url?s=${encodeURIComponent(token)}`, { signal: AbortSignal.timeout(25000) })).json();
  console.log('resolve url ok=', u.ok, 'proxied=', u.proxied);

  // 普通 GET：读前 64KB
  const r = await fetch(u.url, { signal: AbortSignal.timeout(40000) });
  const ab = await r.arrayBuffer();
  console.log('GET status', r.status, 'ctype=', r.headers.get('content-type'), 'len=', ab.byteLength);
  const head = new Uint8Array(ab.slice(0, 4));
  console.log('magic bytes', Array.from(head).map(b => b.toString(16).padStart(2, '0')).join(' '));

  // Range: bytes=0-1023
  const r2 = await fetch(u.url, { headers: { Range: 'bytes=0-1023' }, signal: AbortSignal.timeout(40000) });
  const ab2 = await r2.arrayBuffer();
  console.log('RANGE status', r2.status, 'content-range=', r2.headers.get('content-range'), 'len=', ab2.byteLength);
})();
