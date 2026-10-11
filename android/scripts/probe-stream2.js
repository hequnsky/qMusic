const GW = 'http://127.0.0.1:3000';
const kw = encodeURIComponent('晴天 周杰伦');
(async () => {
  const j = await (await fetch(`${GW}/multi/search?source=kugou&keyword=${kw}&limit=3`, { signal: AbortSignal.timeout(20000) })).json();
  const token = j.songs[0].token;
  const u = await (await fetch(`${GW}/multi/song/url?s=${encodeURIComponent(token)}`, { signal: AbortSignal.timeout(25000) })).json();
  console.log('url json:', JSON.stringify(u).slice(0, 400));
  const r = await fetch(u.url, { signal: AbortSignal.timeout(40000) });
  const t = await r.text();
  console.log('stream status', r.status, 'body:', t.slice(0, 400));
})();
