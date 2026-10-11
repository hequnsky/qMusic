const GW = 'http://127.0.0.1:3000';
(async () => {
  // 拿一个 id
  const j = await (await fetch(`${GW}/search?keywords=${encodeURIComponent('屋顶')}&limit=3`, { signal: AbortSignal.timeout(20000) })).json();
  const ids = j.result.songs.map(s => s.id);
  console.log('ids =', ids.join(','));

  const cands = [
    `${GW}/song/detail?ids=${ids.join(',')}`,
    `${GW}/song/detail?ids=${JSON.stringify(ids)}`,
  ];
  for (const u of cands) {
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(20000) });
      const t = await r.text();
      const o = JSON.parse(t);
      const songs = o.songs || [];
      console.log('\n', u.replace(GW, ''), 'status', r.status, 'songs', songs.length);
      songs.slice(0, 3).forEach(s => console.log('   -', s.name, '| al.picUrl =', s.al?.picUrl));
    } catch (e) { console.log('ERR', u, e.message); }
  }
})();
