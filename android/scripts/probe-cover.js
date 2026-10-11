(async () => {
  const r = await fetch('http://127.0.0.1:3000/search?keywords=' + encodeURIComponent('屋顶') + '&limit=3', { signal: AbortSignal.timeout(20000) });
  const j = await r.json();
  const songs = j?.result?.songs || [];
  console.log('songs:', songs.length);
  for (const s of songs.slice(0, 3)) {
    console.log('-', s.name, '| al.picUrl =', JSON.stringify(s.al?.picUrl), '| album.picUrl =', JSON.stringify(s.album?.picUrl));
  }
  // 同时看多音源接口
  const r2 = await fetch('http://127.0.0.1:3000/multi/search?source=netease&keyword=' + encodeURIComponent('屋顶') + '&limit=3', { signal: AbortSignal.timeout(20000) });
  const j2 = await r2.json();
  console.log('\nmulti songs:', (j2.songs || []).length);
  for (const s of (j2.songs || []).slice(0, 3)) {
    console.log('-', s.name, '| cover =', JSON.stringify(s.cover), '| token.len =', (s.token || '').length);
  }
})();
