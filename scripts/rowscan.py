"""按行扫描截图，输出每个「有亮像素」的行段（用于判断歌词高亮行位置）。
用法: python rowscan.py <png> [yStart] [yEnd] [阈值]
"""
import sys
from bright import load

path = sys.argv[1]
y0 = int(sys.argv[2]) if len(sys.argv) > 2 else 0
y1 = int(sys.argv[3]) if len(sys.argv) > 3 else 10**9
th = int(sys.argv[4]) if len(sys.argv) > 4 else 60

w, h, nch, px = load(path)
y1 = min(y1, h)
stride = w * nch
print(f'{path} {w}x{h} 扫描 y={y0}..{y1} 阈值={th}')

runs = []
cur = None
for y in range(y0, y1):
    base = y * stride
    mx = 0
    for x in range(0, w):
        o = base + x * nch
        r, g, b = px[o], px[o+1], px[o+2]
        lum = (299*r + 587*g + 114*b) // 1000
        if lum > mx:
            mx = lum
            if mx >= 250:
                break
    if mx >= th:
        if cur is None:
            cur = [y, y, mx]
        else:
            cur[1] = y
            cur[2] = max(cur[2], mx)
    else:
        if cur is not None:
            runs.append(cur)
            cur = None
if cur is not None:
    runs.append(cur)

for a, b, m in runs:
    kind = 'WHITE(当前行?)' if m >= 250 else ('main' if m > 95 else 'trans')
    print(f'  y {a:4d}-{b:4d}  center={(a+b)//2:4d}  maxLum={m:3d}  {kind}')
