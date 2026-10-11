const GW = 'http://127.0.0.1:3000';
(async () => {
  const show = async (label, url, pick) => {
    try {
      const j = await (await fetch(url, { signal: AbortSignal.timeout(25000) })).json();
      const arr = pick(j);
      const withCover = arr.filter(s => s && (s.al?.picUrl || s.album?.picUrl)).length;
      console.log(`${label}: n=${arr.length} withCover=${withCover}  e.g. ${arr[0]?.name}`);
    } catch (e) { console.log(label, 'ERR', e.message); }
  };

  await show('search/suggest', `${GW}/search/suggest?keywords=${encodeURIComponent('Jay')}`, j => j.result?.songs || []);
  const top = await (await fetch(`${GW}/toplist`, { signal: AbortSignal.timeout(20000) })).json();
  const tid = top.list?.[0]?.id;
  console.log('toplist[0] =', top.list?.[0]?.name, tid);
  if (tid) await show('playlist/detail(tracks)', `${GW}/playlist/detail?id=${tid}`, j => j.playlist?.tracks || []);
  const pl = await (await fetch(`${GW}/personalized?limit=3`, { signal: AbortSignal.timeout(20000) })).json();
  console.log('personalized[0] =', pl.result?.[0]?.name, pl.result?.[0]?.id, 'cover=', String(pl.result?.[0]?.picUrl).slice(0, 50));
})();
