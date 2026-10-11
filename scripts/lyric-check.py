"""歌词页「当前行是否居中」的像素级检查。

把两件事对齐到同一时刻的两份产物上：
  1) 截图里最亮的那条白字行（当前行是 Color.White，非当前行 alpha=0.35 → 亮度 107）
  2) uiautomator dump 出来的文本节点坐标

用法: python scripts/lyric-check.py <screen.png> <ui.xml> [视口中心y]
输出: 当前行文本 / 它的垂直中心 / 与视口中心的偏差 / 水平中心
"""
import re
import sys

from bright import load


def parse_ui(path):
    rows = []
    for tag in re.findall(r'<node\b[^>]*>', open(path, encoding='utf-8').read()):
        def attr(name):
            m = re.search(r'%s="([^"]*)"' % name, tag)
            return m.group(1) if m else ''
        bounds = attr('bounds')
        label = attr('text') or attr('content-desc')
        if not bounds or not label:
            continue
        b = re.match(r'\[(\d+),(\d+)]\[(\d+),(\d+)]', bounds)
        if not b:
            continue
        x1, y1, x2, y2 = (int(v) for v in b.groups())
        rows.append({'label': label, 'x1': x1, 'y1': y1, 'x2': x2, 'y2': y2,
                     'cy': (y1 + y2) // 2, 'cx': (x1 + x2) // 2})
    return rows


def brightest_band(path, y0, y1, th=240):
    w, h, nch, px = load(path)
    y1 = min(y1, h)
    stride = w * nch
    runs, cur = [], None
    for y in range(y0, y1):
        base = y * stride
        mx = 0
        for x in range(w):
            o = base + x * nch
            lum = (299 * px[o] + 587 * px[o + 1] + 114 * px[o + 2]) // 1000
            if lum > mx:
                mx = lum
                if mx >= 255:
                    break
        if mx >= th:
            if cur is None:
                cur = [y, y, mx]
            else:
                cur[1] = y
                cur[2] = max(cur[2], mx)
        elif cur is not None:
            runs.append(cur)
            cur = None
    if cur is not None:
        runs.append(cur)
    return runs


def group_items(rows):
    """把「原文 + 译文」两个相邻文本节点并成一个歌词项。

    LazyColumn 的每一项 = Column(原文[, 译文])，accessibility 只上报 Text 节点，
    项与项之间隔着 2×9dp 的 padding（实测 ~64px），项内原文与译文只隔 3dp（~11px）。
    用「相邻节点间隙 < 30px」判定同属一项。
    项的中心 = (首节点.y1 + 末节点.y2)/2 —— 上下 padding 对称，不影响中心。
    """
    items = []
    for r in rows:
        if items and r['y1'] - items[-1]['y2'] < 30:
            items[-1]['y2'] = r['y2']
            items[-1]['parts'] += 1
            items[-1]['label'] += ' / ' + r['label']
        else:
            items.append({'y1': r['y1'], 'y2': r['y2'], 'label': r['label'],
                          'cx': r['cx'], 'parts': 1})
    for it in items:
        it['cy'] = (it['y1'] + it['y2']) // 2
    return items


def main():
    png, xml = sys.argv[1], sys.argv[2]
    center = int(sys.argv[3]) if len(sys.argv) > 3 else 1309
    rows = [r for r in parse_ui(xml) if r['y1'] > 320]   # 排除顶栏
    items = group_items(rows)
    bands = brightest_band(png, 320, 2620)
    print(f'{png} + {xml}   视口中心(歌词区)={center}')
    if not bands:
        print('  [FAIL] 歌词区没有找到纯白行 —— 当前行没有高亮')
        for it in items[:3]:
            print(f'        参考: {it["label"][:24]}  cy={it["cy"]}')
        return
    # 多条白色带可能来自同一行折行，取它们的**整体**范围去匹配歌词项
    top, bottom = bands[0][0], bands[-1][1]
    cy = (top + bottom) // 2
    hit = min(items, key=lambda it: abs(it['cy'] - cy))
    off = hit['cy'] - center
    flag = 'OK 当前行居中' if abs(off) <= 12 else f'OFF-CENTER 偏差 {off:+d}px'
    print(f'  白色行 y {top}-{bottom} (maxLum={max(b[2] for b in bands)})')
    print(f'    → 当前歌词项: {hit["label"][:40]}')
    print(f'    → 垂直中心 {hit["cy"]}（视口中心 {center}，偏差 {off:+d}px）')
    print(f'    → 水平中心 {hit["cx"]}（屏幕中线 620）   [{flag}]')


if __name__ == '__main__':
    main()
