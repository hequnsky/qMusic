"""从播放页截图定位唱片（旋转圆盘）圆心。
用法: python disc_center.py <png>
方法：逐行统计 [x=420..820] 区间内与背景色 RGB(34,21,46) 差异较大的像素数，
差异大的行构成唱片竖直跨度，取中点作圆心 y；x 固定取 620（水平居中）。
"""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from bright import load

def diff(r, g, b, tr, tg, tb):
    return abs(r-tr) + abs(g-tg) + abs(b-tb)

def main():
    path = sys.argv[1]
    w, h, nch, px = load(path)
    stride = w * nch
    bg = (34, 21, 46)
    x0, x1 = 420, 820
    rows = []
    for y in range(200, 2400):
        base = y * stride
        c = 0
        for x in range(x0, x1, 2):
            o = base + x * nch
            if diff(px[o], px[o+1], px[o+2], *bg) > 60:
                c += 1
        rows.append((y, c))
    # 找峰值区间
    vals = [c for _, c in rows]
    mx = max(vals)
    if mx < 20:
        print('未找到唱片区域'); return
    lo = hi = None
    for y, c in rows:
        if c > mx * 0.4:
            if lo is None: lo = y
            hi = y
    cy = (lo + hi) // 2
    print(f'{path}  唱片竖直跨度 y=[{lo},{hi}]  圆心≈(620,{cy})  峰值行像素数={mx}')

if __name__ == '__main__':
    main()
