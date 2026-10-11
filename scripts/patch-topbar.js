// 把各二级页顶栏硬编码的 top = 40.dp / 44.dp 换成动态的 topBarTopPadding()
const fs = require('fs');
const path = require('path');

const ROOT = 'F:/project/AiCoding/deepseek/qMusic/android/app/src/main/java/com/qmusic';
const files = [
  'ui/category/CategoryMusicScreen.kt',
  'ui/comment/CommentScreen.kt',
  'ui/download/DownloadScreen.kt',
  'ui/mine/LoginScreen.kt',
  'ui/mine/SongCollectionScreen.kt',
  'ui/player/PlayerScreen.kt',
  'ui/playlist/HotPlaylistScreen.kt',
  'ui/playlist/LocalMusicScreen.kt',
  'ui/playlist/MyPlaylistsScreen.kt',
  'ui/search/HotSearchScreen.kt',
  'ui/search/SearchScreen.kt',
  'ui/toplist/ToplistScreen.kt',
];

const IMPORT = 'import com.qmusic.ui.components.topBarTopPadding';
let changed = 0;

for (const rel of files) {
  const file = path.join(ROOT, rel);
  let s = fs.readFileSync(file, 'utf8');
  const before = s;

  const hits = s.match(/top = (?:40|44)\.dp/g);
  s = s.replace(/top = (?:40|44)\.dp/g, 'top = topBarTopPadding()');

  if (!s.includes(IMPORT)) {
    const lines = s.split('\n');
    const all = lines.map((l, i) => [l, i]).filter(([l]) => l.startsWith('import '));
    if (!all.length) throw new Error('no import block: ' + rel);
    const last = all[all.length - 1][1];
    lines.splice(last + 1, 0, IMPORT);
    s = lines.join('\n');
  }

  if (s !== before) {
    fs.writeFileSync(file, s);
    changed++;
    console.log(`patched ${rel}  (${hits ? hits.length : 0} padding)`);
  } else {
    console.log(`no change ${rel}`);
  }
}
console.log(`\n${changed} files updated`);
