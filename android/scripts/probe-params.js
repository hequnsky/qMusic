(async () => {
  const tries = [
    'http://127.0.0.1:3100/search?source=kugou&keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3',
    'http://127.0.0.1:3100/search?source=kugou&keyword=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3',
    'http://127.0.0.1:3100/search?source=kugou&q=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3',
    'http://127.0.0.1:3100/multi/search?source=kugou&keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3',
    'http://127.0.0.1:3100/api/search?source=kugou&keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6',
  ];
  for (const u of tries) {
    try {
      const r = await fetch(u, { signal: AbortSignal.timeout(15000) });
      const t = await r.text();
      console.log(r.status, u.replace('http://127.0.0.1:3100',''), '=>', t.slice(0, 180).replace(/\n/g,' '));
    } catch (e) { console.log('ERR', u, e.message); }
  }
})();
