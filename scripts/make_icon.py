"""
应用图标生成器。

设计说明：
    图形 = 等距立方体（货架/库存隐喻）+ 三条白色分层棒
    纯几何构成，无文字 —— 文字在 48dp 桌面上必然糊成一团
    配色沿用应用主色 #007DFF

为什么用纯 Python 写 PNG：
    不依赖 PIL / cairo / ImageMagick。
    自己算像素、自己 deflate、自己写 PNG 块，
    在任何只有 Python 的机器上都能复现。

输出：
    app/src/main/res/drawable/ic_launcher_foreground.png   432x432 透明底
    app/src/main/res/drawable/ic_launcher_background.png   432x432 纯色渐变
    app/src/main/res/mipmap-*/ic_launcher.png              48/72/96/144/192
    docs/images/app-icon.png                               512x512 展示

用法：python scripts/make_icon.py
"""

import math
import os
import struct
import zlib

# ============================================================
# PNG 编码
# ============================================================


def write_png(path, width, height, rgba_rows):
    """rgba_rows: list[bytes]，每行 width*4 字节（RGBA）"""
    raw = b""
    for row in rgba_rows:
        raw += b"\x00" + row  # filter type 0 (None)

    compressed = zlib.compress(raw, 9)

    def chunk(typ, data):
        c = struct.pack(">I", len(data)) + typ + data
        c += struct.pack(">I", zlib.crc32(typ + data) & 0xFFFFFFFF)
        return c

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", compressed)
    png += chunk(b"IEND", b"")

    with open(path, "wb") as f:
        f.write(png)


class Canvas:
    """极简 RGBA 画布，带超采样抗锯齿"""

    def __init__(self, w, h, ss=3):
        self.w = w
        self.h = h
        self.ss = ss          # 超采样倍数
        self.W = w * ss
        self.H = h * ss
        self.buf = bytearray(self.W * self.H * 4)  # 全透明

    def _idx(self, x, y):
        return (y * self.W + x) * 4

    def blend(self, x, y, r, g, b, a):
        """在超采样坐标上做源覆盖混合（a: 0-1）"""
        if x < 0 or y < 0 or x >= self.W or y >= self.H:
            return
        if a <= 0:
            return
        i = self._idx(x, y)
        if a >= 1:
            self.buf[i] = r
            self.buf[i + 1] = g
            self.buf[i + 2] = b
            self.buf[i + 3] = 255
            return
        # 与该像素已有颜色混合（已是预乘近似，够用）
        da = self.buf[i + 3] / 255.0
        oa = a + da * (1 - a)
        if oa <= 0:
            return
        for k, v in enumerate((r, g, b)):
            dc = self.buf[i + k]
            self.buf[i + k] = int((v * a + dc * da * (1 - a)) / oa)
        self.buf[i + 3] = int(oa * 255)

    def fill_polygon(self, pts, color):
        """扫描线填充多边形。pts: [(x,y)] 在最终坐标上（会乘 ss）"""
        r, g, b, a = color
        P = [(p[0] * self.ss, p[1] * self.ss) for p in pts]
        n = len(P)
        y_min = max(0, int(math.floor(min(p[1] for p in P))))
        y_max = min(self.H - 1, int(math.ceil(max(p[1] for p in P))))

        for y in range(y_min, y_max + 1):
            yc = y + 0.5
            xs = []
            for i in range(n):
                x1, y1 = P[i]
                x2, y2 = P[(i + 1) % n]
                if y1 == y2:
                    continue
                if (y1 <= yc < y2) or (y2 <= yc < y1):
                    t = (yc - y1) / (y2 - y1)
                    xs.append(x1 + t * (x2 - x1))
            xs.sort()
            for i in range(0, len(xs) - 1, 2):
                x_start = max(0, int(math.floor(xs[i])))
                x_end = min(self.W - 1, int(math.ceil(xs[i + 1])))
                for x in range(x_start, x_end + 1):
                    self.blend(x, y, r, g, b, a)

    def fill_round_rect(self, x, y, w, h, radius, color):
        """圆角矩形，用多边形近似（每条弧 8 段）"""
        pts = []
        r = min(radius, w / 2, h / 2)
        corners = [
            (x + r, y + r, math.pi, math.pi * 1.5),                 # 左上
            (x + w - r, y + r, math.pi * 1.5, math.pi * 2),          # 右上
            (x + w - r, y + h - r, 0, math.pi * 0.5),                # 右下
            (x + r, y + h - r, math.pi * 0.5, math.pi),              # 左下
        ]
        for cx, cy, a0, a1 in corners:
            steps = 8
            for i in range(steps + 1):
                ang = a0 + (a1 - a0) * i / steps
                pts.append((cx + r * math.cos(ang), cy + r * math.sin(ang)))
        self.fill_polygon(pts, color)

    def fill_gradient_rect(self, x, y, w, h, c1, c2, vertical=True):
        """线性渐变矩形（逐像素，超采样坐标）"""
        r1, g1, b1 = c1
        r2, g2, b2 = c2
        X0 = int(x * self.ss)
        Y0 = int(y * self.ss)
        X1 = int((x + w) * self.ss)
        Y1 = int((y + h) * self.ss)
        span = (h if vertical else w) * self.ss
        if span <= 0:
            return
        for yy in range(max(0, Y0), min(self.H, Y1)):
            t = (yy - Y0) / span if vertical else 0
            t = max(0.0, min(1.0, t))
            r = int(r1 + (r2 - r1) * t)
            g = int(g1 + (g2 - g1) * t)
            b = int(b1 + (b2 - b1) * t)
            for xx in range(max(0, X0), min(self.W, X1)):
                if not vertical:
                    t2 = (xx - X0) / ((w * self.ss) or 1)
                    r = int(r1 + (r2 - r1) * t2)
                    g = int(g1 + (g2 - g1) * t2)
                    b = int(b1 + (b2 - b1) * t2)
                i = (yy * self.W + xx) * 4
                self.buf[i] = r
                self.buf[i + 1] = g
                self.buf[i + 2] = b
                self.buf[i + 3] = 255

    def fill_radial(self, w, h, center, inner, outer, radius):
        """径向渐变（简单线性插值，够用）"""
        cr, cg, cb = inner
        orr, og, ob = outer
        cx, cy = center
        for yy in range(self.H):
            for xx in range(self.W):
                dx = (xx / self.ss - cx) / radius
                dy = (yy / self.ss - cy) / radius
                d = min(1.0, math.sqrt(dx * dx + dy * dy))
                r = int(cr + (orr - cr) * d)
                g = int(cg + (og - cg) * d)
                b = int(cb + (ob - cb) * d)
                i = (yy * self.W + xx) * 4
                self.buf[i] = r
                self.buf[i + 1] = g
                self.buf[i + 2] = b
                self.buf[i + 3] = 255

    def downsample(self):
        """超采样 -> 最终分辨率，返回 RGBA 行列表"""
        ss = self.ss
        rows = []
        for y in range(self.h):
            row = bytearray(self.w * 4)
            for x in range(self.w):
                ar = ag = ab = aa = 0
                for sy in range(ss):
                    for sx in range(ss):
                        i = ((y * ss + sy) * self.W + (x * ss + sx)) * 4
                        ar += self.buf[i]
                        ag += self.buf[i + 1]
                        ab += self.buf[i + 2]
                        aa += self.buf[i + 3]
                n = ss * ss
                j = x * 4
                row[j] = ar // n
                row[j + 1] = ag // n
                row[j + 2] = ab // n
                row[j + 3] = aa // n
            rows.append(bytes(row))
        return rows


# ============================================================
# 图形定义
# ============================================================

# 配色
C_LIGHT = (150, 210, 255)
C_MAIN = (0, 125, 255)
C_DEEP = (0, 84, 204)
C_DARK = (0, 56, 140)
C_BG_IN = (0, 132, 255)
C_BG_OUT = (0, 60, 150)
C_WHITE = (255, 255, 255)


def draw_cube(cv, scale=1.0, offset=(0, 0)):
    """等距立方体 + 三条分层棒

    scale: 相对 432 基准画布的缩放；实际坐标 = base * scale。
           调用方需传 (画布尺寸 / 432)，保证不同尺寸下比例一致。
    offset: 相对画布中心的偏移（同样按 432 基准）
    """
    # 画布中心（用画布实际尺寸，不能用固定的 432）
    cx = cv.w / 2.0 + offset[0] * scale
    cy = cv.h / 2.0 + offset[1] * scale

    # 自适应图标的「安全区」：108dp 画布中只有中间 72dp 保证可见。
    # 主体要撑满安全区，不能留太多空白，否则桌面缩小后看不清。
    halfW = 122 * scale
    halfH = 70 * scale      # 等距投影 30° 时约 0.577 倍
    depth = 96 * scale

    # 顶面四顶点（菱形）
    topC = (cx, cy - halfH - depth / 2)
    topR = (cx + halfW, cy - depth / 2)
    topB = (cx, cy + halfH - depth / 2)
    topL = (cx - halfW, cy - depth / 2)

    # 左前侧面（四个角：topL, topB, bottomB, bottomL）
    botB = (topB[0], topB[1] + depth)
    botL = (topL[0], topL[1] + depth)
    cv.fill_polygon([topL, topB, botB, botL], (C_MAIN[0], C_MAIN[1], C_MAIN[2], 1.0))

    # 右前侧面（更暗，制造体积）
    botR = (topR[0], topR[1] + depth)
    cv.fill_polygon([topR, topB, botB, botR], (C_DARK[0], C_DARK[1], C_DARK[2], 1.0))

    # 顶面（最亮）
    cv.fill_polygon([topC, topR, topB, topL], (C_LIGHT[0], C_LIGHT[1], C_LIGHT[2], 1.0))

    # ---- 左前侧面上的三条分层棒 ----
    # 该面的局部坐标系：左上角 topL，右上角 topB，右下角 botB
    # 沿 topL→topB 方向是「宽」，沿 topL→botL 方向是「高」
    # 用双线性插值把局部 (u, v) 映射到画布坐标，棒就自然贴合斜面

    def face_uv(u, v):
        """u: 0..1 从左边到右边（沿斜面）；v: 0..1 从上到下"""
        ax = topL[0] + (topB[0] - topL[0]) * u
        ay = topL[1] + (topB[1] - topL[1]) * u
        bx = botL[0] + (botB[0] - botL[0]) * u
        by = botL[1] + (botB[1] - botL[1]) * u
        return (ax + (bx - ax) * v, ay + (by - ay) * v)

    # 三条棒在面内的相对位置（v 方向从上往下排布）
    #
    # 小尺寸（< 96px）只画两条且加粗 —— 48px 上第三条只有 1 个像素高，
    # 混色后必然糊成噪点，反而破坏辨识度。图标设计要让「最小尺寸」先成立。
    small = cv.w < 96

    if small:
        bar_vs = [0.34, 0.60]
        bar_thick = 0.135
        u_start = 0.15
        u_lengths = [0.70, 0.52]
    else:
        bar_vs = [0.30, 0.53, 0.76]
        bar_thick = 0.085
        u_start = 0.16
        u_lengths = [0.66, 0.54, 0.42]

    for i, v in enumerate(bar_vs):
        u_end = u_start + u_lengths[i]
        p1 = face_uv(u_start, v)
        p2 = face_uv(u_end, v)
        p3 = face_uv(u_end, v + bar_thick)
        p4 = face_uv(u_start, v + bar_thick)
        alpha = 1.0 if i == 0 else 0.75
        cv.fill_polygon([p1, p2, p3, p4], (255, 255, 255, alpha))


def make_foreground(path, size=432):
    cv = Canvas(size, size, ss=4)
    draw_cube(cv, scale=size / 432.0)
    write_png(path, size, size, cv.downsample())

def make_background(path, size=432):
    cv = Canvas(size, size, ss=2)
    cv.fill_radial(size, size, (size * 0.5, size * 0.40), C_BG_IN, C_BG_OUT, size * 0.78)
    write_png(path, size, size, cv.downsample())


def make_legacy(path, size):
    """传统图标：渐变背景 + 缩放前景"""
    cv = Canvas(size, size, ss=4)
    # 背景对角渐变
    for y in range(cv.H):
        t = y / cv.H
        r = int(C_BG_IN[0] + (C_BG_OUT[0] - C_BG_IN[0]) * t)
        g = int(C_BG_IN[1] + (C_BG_OUT[1] - C_BG_IN[1]) * t)
        b = int(C_BG_IN[2] + (C_BG_OUT[2] - C_BG_IN[2]) * t)
        for x in range(cv.W):
            i = (y * cv.W + x) * 4
            cv.buf[i] = r
            cv.buf[i + 1] = g
            cv.buf[i + 2] = b
            cv.buf[i + 3] = 255

    # 前景按画布尺寸等比缩放。留 10% 内边距，
    # 因为自适应图标的安全区只有中心 66%，传统图标无此限制但要避免贴边。
    draw_cube(cv, scale=(size / 432.0) * 1.0)
    write_png(path, size, size, cv.downsample())


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    root = os.path.dirname(here)
    res = os.path.join(root, "app", "src", "main", "res")
    docs = os.path.join(root, "docs", "images")

    os.makedirs(docs, exist_ok=True)

    print("生成自适应图标前景...")
    make_foreground(os.path.join(res, "drawable", "ic_launcher_foreground.png"))
    print("  432x432 透明底")

    print("生成自适应图标背景...")
    make_background(os.path.join(res, "drawable", "ic_launcher_background.png"))
    print("  432x432 径向渐变")

    densities = [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]
    for name, px in densities:
        d = os.path.join(res, "mipmap-" + name)
        os.makedirs(d, exist_ok=True)
        make_legacy(os.path.join(d, "ic_launcher.png"), px)
        print(f"  mipmap-{name}/ic_launcher.png {px}x{px}")

    print("生成展示大图...")
    make_legacy(os.path.join(docs, "app-icon.png"), 512)
    print("  docs/images/app-icon.png 512x512")

    print("\n完成。")


if __name__ == "__main__":
    main()
