// 解析 uiautomator dump 出的 xml，打印控件文本与中心坐标
// 用法: node scripts/ui.js <ui.xml> [过滤关键字]
const fs = require('fs');

const file = process.argv[2];
const filter = process.argv[3];
if (!file) {
  console.error('usage: node scripts/ui.js <ui.xml> [filter]');
  process.exit(1);
}

const xml = fs.readFileSync(file, 'utf8');
const nodeRe = /<node\b[^>]*>/g;
const attr = (tag, name) => {
  const m = tag.match(new RegExp(`${name}="([^"]*)"`));
  return m ? m[1] : '';
};

const rows = [];
let m;
while ((m = nodeRe.exec(xml))) {
  const tag = m[0];
  const text = attr(tag, 'text');
  const desc = attr(tag, 'content-desc');
  const bounds = attr(tag, 'bounds');
  if (!bounds) continue;
  const b = bounds.match(/\[(\d+),(\d+)\]\[(\d+),(\d+)\]/);
  if (!b) continue;
  const [x1, y1, x2, y2] = [+b[1], +b[2], +b[3], +b[4]];
  const label = text || desc;
  if (!label) continue;
  if (filter && !label.includes(filter)) continue;
  rows.push({
    label,
    cx: Math.round((x1 + x2) / 2),
    cy: Math.round((y1 + y2) / 2),
    x1, y1, x2, y2,
  });
}

if (!rows.length) {
  console.log('(no match)');
} else {
  for (const r of rows) {
    console.log(`${r.label}\tc=${r.cx},${r.cy}\tbox=[${r.x1},${r.y1}][${r.x2},${r.y2}]`);
  }
}
