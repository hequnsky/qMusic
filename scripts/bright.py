"""极简 PNG 亮度采样：找出截图里每一行文字的最亮像素，用于判断哪一行是「当前歌词」。

用法: python bright.py <png> <y1> <y2> ...
只支持 8bit RGB/RGBA 非隔行 PNG（安卓 screencap 默认输出）。
"""
import sys, zlib, struct

def load(path):
    data = open(path, 'rb').read()
    assert data[:8] == b'\x89PNG\r\n\x1a\n', 'not png'
    pos = 8
    idat = b''
    w = h = bitd = ctype = None
    while pos < len(data):
        ln = struct.unpack('>I', data[pos:pos+4])[0]
        typ = data[pos+4:pos+8]
        chunk = data[pos+8:pos+8+ln]
        if typ == b'IHDR':
            w, h, bitd, ctype, comp, filt, inter = struct.unpack('>IIBBBBB', chunk)
            assert bitd == 8 and inter == 0, (bitd, inter)
        elif typ == b'IDAT':
            idat += chunk
        elif typ == b'IEND':
            break
        pos += 12 + ln
    raw = zlib.decompress(idat)
    nch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = w * nch
    out = bytearray(h * stride)
    prev = bytearray(stride)
    p = 0
    for y in range(h):
        f = raw[p]; p += 1
        line = bytearray(raw[p:p+stride]); p += stride
        if f == 1:
            for i in range(nch, stride):
                line[i] = (line[i] + line[i-nch]) & 0xFF
        elif f == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif f == 3:
            for i in range(stride):
                a = line[i-nch] if i >= nch else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif f == 4:
            for i in range(stride):
                a = line[i-nch] if i >= nch else 0
                b = prev[i]
                c = prev[i-nch] if i >= nch else 0
                pa = abs(b - c); pb = abs(a - c); pc = abs(a + b - 2*c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y*stride:(y+1)*stride] = line
        prev = line
    return w, h, nch, out

def main():
    path = sys.argv[1]
    ys = [int(v) for v in sys.argv[2:]]
    w, h, nch, px = load(path)
    print(f'{path}  {w}x{h}  channels={nch}')
    stride = w * nch
    for y in ys:
        if y < 0 or y >= h:
            print(f'y={y}  (out of range)')
            continue
        mx = 0; mxr = mxg = mxb = 0
        base = y * stride
        for x in range(0, w):
            o = base + x*nch
            r, g, b = px[o], px[o+1], px[o+2]
            lum = (299*r + 587*g + 114*b) // 1000
            if lum > mx:
                mx = lum; mxr, mxg, mxb = r, g, b
        print(f'y={y}  maxLum={mx:3d}  rgb=({mxr},{mxg},{mxb})')

if __name__ == '__main__':
    main()
