// 服务端连通性探测（纯 fetch，无子进程，适配本机沙箱约束）
const targets = [
  ['go-health', 'http://127.0.0.1:3100/health'],
  ['go-sources', 'http://127.0.0.1:3100/sources'],
  ['gw-multi-sources', 'http://127.0.0.1:3000/multi/sources'],
  ['gw-search-netease', 'http://127.0.0.1:3000/search?keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3'],
  ['gw-search-kugou', 'http://127.0.0.1:3000/multi/search?source=kugou&keywords=%E5%91%A8%E6%9D%B0%E4%BC%A6&limit=3'],
];

(async () => {
  for (const [name, url] of targets) {
    try {
      const r = await fetch(url, { signal: AbortSignal.timeout(15000) });
      const t = await r.text();
      console.log(name.padEnd(20), r.status, t.slice(0, 220).replace(/\n/g, ' '));
    } catch (e) {
      console.log(name.padEnd(20), 'ERR', e.message);
    }
  }
})();
