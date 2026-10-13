"""扫描主题选择面板截图，统计各主题强调色像素分布。
用法: python theme_preview_scan.py <png>
检测 6 套主题的强调色/渐变主色是否都出现在截图中（证明迷你预览图已渲染）。
"""
import sys
sys.path.insert(0, __file__.rsplit('/', 1)[0])
from bright import load

# 各主题渐变主色（ThemeStyle.brand.gradientStart / accent）
THEMES = {
    "经典红": ((255, 75, 92), (255, 138, 61)),    # FF4B5C / FF8A3D
    "星河紫": ((138, 92, 255), (176, 92, 255)),  # 8A5CFF / B05CFF
    "深海蓝": ((61, 165, 255), (46, 123, 255)),  # 3DA5FF / 2E7BFF
    "极光绿": ((43, 217, 160), (22, 194, 194)),  # 2BD9A0 / 16C2C2
    "蜜桃粉": ((255, 111, 165), (255, 159, 107)), # FF6FA5 / FF9F6B
    "浅色":   ((255, 75, 92), (255, 138, 61)),    # 与经典红同渐变
}

def near(r, g, b, tr, tg, tb, tol=38):
    return abs(r-tr) <= tol and abs(g-tg) <= tol and abs(b-tb) <= tol

def main():
    path = sys.argv[1]
    w, h, nch, px = load(path)
    counts = {name: 0 for name in THEMES}
    stride = w * nch
    for y in range(0, h, 1):
        base = y * stride
        for x in range(0, w, 1):
            o = base + x * nch
            r, g, b = px[o], px[o+1], px[o+2]
            for name, colors in THEMES.items():
                for (tr, tg, tb) in colors:
                    if near(r, g, b, tr, tg, tb):
                        counts[name] += 1
                        break
                else:
                    continue
                break
    print(f'{path}  {w}x{h}')
    total = 0
    for name, c in counts.items():
        print(f'{name}: {c} px')
        total += c
    print(f'--- 主题预览强调色像素总计: {total} ---')
    non_zero = [n for n, c in counts.items() if c > 30]
    print(f'检测到预览图的主题: {len(non_zero)}/{len(THEMES)} -> {non_zero}')
    if len(non_zero) >= 5:
        print('结论: 主题预览图渲染正常（≥5 套主题的强调色可见）')
    else:
        print('结论: 预览图渲染可能不完整，需人工核对')

if __name__ == '__main__':
    main()