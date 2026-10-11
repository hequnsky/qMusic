"""扫描截图中强调色像素：确认主题切换后强调色从红变成蓝。
用法: python accent_scan.py <png>
统计接近「深海蓝强调色」RGB(75,139,255) 与「经典红强调色」RGB(255,75,75) 的像素数量。
"""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from bright import load

def near(r, g, b, tr, tg, tb, tol=40):
    return abs(r-tr) <= tol and abs(g-tg) <= tol and abs(b-tb) <= tol

def main():
    path = sys.argv[1]
    w, h, nch, px = load(path)
    # 深海蓝强调色 (0xFF4B8BFF) -> R=75 G=139 B=255
    blue = (75, 139, 255)
    # 经典红强调色 (0xFFFF4B4B) -> R=255 G=75 B=75
    red = (255, 75, 75)
    bc = rc = 0
    stride = w * nch
    for y in range(0, h, 2):          # 隔行扫描提速
        base = y * stride
        for x in range(0, w, 2):
            o = base + x * nch
            r, g, b = px[o], px[o+1], px[o+2]
            if near(r, g, b, *blue):
                bc += 1
            elif near(r, g, b, *red):
                rc += 1
    print(f'{path}  {w}x{h}')
    print(f'深海蓝强调色像素(≈): {bc}')
    print(f'经典红强调色像素(≈): {rc}')
    if bc > rc:
        print('结论: 强调色已变为蓝色（主题切换生效）')
    elif rc > bc:
        print('结论: 强调色仍为红色（主题未切换）')
    else:
        print('结论: 两种强调色像素都很少，需人工核对截图')

if __name__ == '__main__':
    main()
