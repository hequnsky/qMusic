const GW = 'http://127.0.0.1:3000';
const kws = ['周杰伦', '告白气球', 'Hello Adele', 'Taylor Swift'];
(async () => {
  for (const s of ['kugou', 'apple']) {
    for (const k of kws) {
      try {
        const sr = await fetch(`${GW}/multi/search?source=${s}&keyword=${encodeURIComponent(k)}&limit=3`, { signal: AbortSignal.timeout(25000) });
        const sj = await sr.json();
        const n = (sj.songs || []).length;
        let extra = '';
        if (n > 0) {
          const ur = await fetch(`${GW}/multi/song/url?s=${encodeURIComponent(sj.songs[0].token)}`, { signal: AbortSignal.timeout(30000) });
          const uj = await ur.json();
          if (uj.ok && uj.url) {
            const rr = await fetch(uj.url, { headers: { Range: 'bytes=0-2047' }, signal: AbortSignal.timeout(40000) });
            extra = ` url=${rr.status} bytes=${(await rr.arrayBuffer()).byteLength}`;
          } else extra = ` urlFAIL ${JSON.stringify(uj).slice(0,90)}`;
        }
        console.log(`${s.padEnd(7)} "${k.padEnd(14)}" search=${n}${extra}`);
      } catch (e) { console.log(`${s.padEnd(7)} "${k}" ERR ${e.message}`); }
    }
  }
})();
