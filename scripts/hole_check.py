"""检查播放页唱片中心是否还有「黑点」（旧版 36dp 近黑实心圆）。
用法: python hole_check.py <png> <cx> <cy> [box=150]
扫描以 (cx,cy) 为中心的 box×box 区域：
- 统计接近旧黑点色 RGB(13,13,15) 的像素数
- 计算该区域颜色方差（方差极低且近黑像素极多 => 黑点仍在）
"""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from bright import load

def main():
    path = sys.argv[1]
    cx, cy = int(sys.argv[2]), int(sys.argv[3])
    box = int(sys.argv[4]) if len(sys.argv) > 4 else 150
    w, h, nch, px = load(path)
    stride = w * nch
    half = box // 2
    x0, x1 = max(0, cx-half), min(w, cx+half)
    y0, y1 = max(0, cy-half), min(h, cy+half)
    rs = gs = bs = 0
    cnt = 0
    near_black = 0
    for y in range(y0, y1):
        base = y * stride
        for x in range(x0, x1):
            o = base + x * nch
            r, g, b = px[o], px[o+1], px[o+2]
            rs += r; gs += g; bs += b; cnt += 1
            if abs(r-13) <= 8 and abs(g-13) <= 8 and abs(b-15) <= 8:
                near_black += 1
    if cnt == 0:
        print('区域越界'); return
    ar, ag, ab = rs//cnt, gs//cnt, bs//cnt
    var = 0
    for y in range(y0, y1):
        base = y * stride
        for x in range(x0, x1):
            o = base + x * nch
            var += (px[o]-ar)**2 + (px[o+1]-ag)**2 + (px[o+2]-ab)**2
    var = var // (cnt*3)
    print(f'{path}  box={box}@{cx},{cy}')
    print(f'平均色 rgb=({ar},{ag},{ab})  颜色方差={var}  近黑像素={near_black}/{cnt}')
    if near_black > cnt * 0.5 and var < 200:
        print('结论: 唱片中心仍是均匀近黑圆点（黑点未去除！）')
    else:
        print('结论: 唱片中心无均匀近黑圆点（黑点已去除 ✓）')

if __name__ == '__main__':
    main()
