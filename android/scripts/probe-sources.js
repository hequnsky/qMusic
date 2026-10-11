// 逐音源搜索探测
const sources = ['netease', 'qq', 'kugou', 'kuwo', 'migu', 'qianqian', 'soda', 'bilibili', 'apple'];
const kw = encodeURIComponent('周杰伦');

(async () => {
  let ok = 0;
  for (const s of sources) {
    const url = `http://127.0.0.1:3100/search?source=${s}&keywords=${kw}&limit=3`;
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(20000) });
      const j = await r.json();
      const n = (j.songs || []).length;
      if (n > 0) ok++;
      const first = n ? `${j.songs[0].name} / ${j.songs[0].artist || j.songs[0].artists}` : '';
      console.log(`${s.padEnd(10)} ${String(r.status).padEnd(4)} songs=${String(n).padEnd(3)} ${String(j.elapsed_ms||'').padEnd(8)} ${first}`);
    } catch (e) {
      console.log(`${s.padEnd(10)} ERR  ${e.message}`);
    }
  }
  console.log(`\nRESULT: ${ok}/${sources.length} sources returned results`);
})();
