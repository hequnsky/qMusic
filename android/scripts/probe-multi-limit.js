const GW = 'http://127.0.0.1:3000';
(async () => {
  for (const kw of ['屋顶', 'Jay']) {
    for (const lim of [10, 30]) {
      const r = await fetch(`${GW}/multi/search?source=netease&keyword=${encodeURIComponent(kw)}&limit=${lim}`, { signal: AbortSignal.timeout(25000) });
      const j = await r.json();
      const n = (j.songs || []).length;
      const withCover = (j.songs || []).filter(s => s.cover).length;
      console.log(`netease "${kw}" limit=${lim} -> songs=${n} withCover=${withCover}  ${n ? j.songs[0].name : ''}`);
    }
  }
  // 直接打 go 服务
  for (const lim of [10, 30, 50]) {
    const r = await fetch(`http://127.0.0.1:3100/search?source=netease&keyword=${encodeURIComponent('屋顶')}&limit=${lim}`, { signal: AbortSignal.timeout(25000) });
    const j = await r.json();
    console.log(`direct netease limit=${lim} -> songs=${(j.songs||[]).length}`);
  }
})();
