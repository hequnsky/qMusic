(async () => {
  const r = await fetch('http://127.0.0.1:3000/search?keywords=' + encodeURIComponent('屋顶') + '&limit=1', { signal: AbortSignal.timeout(20000) });
  const j = await r.json();
  const s = j?.result?.songs?.[0];
  console.log('album =', JSON.stringify(s?.album, null, 1));
  console.log('artists =', JSON.stringify(s?.artists, null, 1).slice(0, 300));
})();
