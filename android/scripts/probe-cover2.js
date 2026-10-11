(async () => {
  const r = await fetch('http://127.0.0.1:3000/search?keywords=' + encodeURIComponent('屋顶') + '&limit=1', { signal: AbortSignal.timeout(20000) });
  const j = await r.json();
  const s = j?.result?.songs?.[0];
  console.log('top-level keys:', Object.keys(j || {}));
  console.log('result keys:', Object.keys(j?.result || {}));
  console.log('song keys:', Object.keys(s || {}));
  console.log('al:', JSON.stringify(s?.al, null, 1).slice(0, 600));
  console.log('album:', JSON.stringify(s?.album, null, 1).slice(0, 600));
})();
