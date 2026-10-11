// 逐音源：搜索 -> 解析播放地址 -> 判断可用性
const GW = 'http://127.0.0.1:3000';
const sources = ['netease', 'qq', 'kugou', 'kuwo', 'migu', 'qianqian', 'soda', 'bilibili', 'apple'];
const kw = encodeURIComponent('晴天');

(async () => {
  let playable = 0, searchable = 0;
  for (const s of sources) {
    let line = `${s.padEnd(10)}`;
    try {
      const sr = await fetch(`${GW}/multi/search?source=${s}&keyword=${kw}&limit=3`, { signal: AbortSignal.timeout(25000) });
      const sj = await sr.json();
      const n = (sj.songs || []).length;
      if (n > 0) searchable++;
      line += ` search=${n}`;
      if (n > 0) {
        const token = sj.songs[0].token;
        const ur = await fetch(`${GW}/multi/song/url?s=${encodeURIComponent(token)}`, { signal: AbortSignal.timeout(30000) });
        const uj = await ur.json();
        if (uj.ok && uj.url) {
          // 真正拉一小段字节
          const rr = await fetch(uj.url, { headers: { Range: 'bytes=0-2047' }, signal: AbortSignal.timeout(40000) });
          const ct = rr.headers.get('content-type') || '';
          const len = (await rr.arrayBuffer()).byteLength;
          const okAudio = rr.status < 400 && len > 500;
          if (okAudio) playable++;
          line += ` url=${rr.status} ct=${ct.split(';')[0]} bytes=${len} ${okAudio ? 'PLAYABLE' : 'BAD'}`;
          if (!okAudio) { const b = await (await fetch(uj.url, {signal:AbortSignal.timeout(30000)})).text(); line += ` body=${b.slice(0,80).replace(/\n/g,' ')}`; }
        } else {
          line += ` url=FAIL ${JSON.stringify(uj).slice(0, 120)}`;
        }
      }
    } catch (e) { line += ` ERR ${e.message}`; }
    console.log(line);
  }
  console.log(`\nSUMMARY: searchable=${searchable}/${sources.length} playable=${playable}/${sources.length}`);
})();
