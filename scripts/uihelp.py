"""设备操作助手：按文字定位元素并点击，抓取裁切后的截图。

裁切偏移（状态栏、导航栏）按当前设备分辨率自动推算，只作用于输出图片；
点击一律使用 uiautomator 给出的真实设备坐标，避免两套坐标系混用。

设备：默认取当前唯一连接的设备；接了多台时用环境变量 ADB_SERIAL 指定。

用法：
    python scripts/uihelp.py tap "知道了"
    python scripts/uihelp.py shot <输出目录> <文件名>
    python scripts/uihelp.py find "EN"
    python scripts/uihelp.py texts
"""
import os
import re
import subprocess
import sys
import tempfile

ADB = r"F:\android-dev\sdk\platform-tools\adb.exe"
LOCAL_TMP = os.path.join(tempfile.gettempdir(), "_ui.xml")


def detect_serial():
    """当前连接的唯一设备；多台时要求显式指定，避免操作到错误的机器。"""
    env = os.environ.get("ADB_SERIAL", "").strip()
    if env:
        return env
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    serials = [
        line.split()[0]
        for line in out.splitlines()[1:]
        if line.strip() and line.split()[-1] == "device"
    ]
    if not serials:
        sys.exit("没有已连接的设备")
    if len(serials) > 1:
        sys.exit(f"连接了多台设备 {serials}，请设置环境变量 ADB_SERIAL 指定")
    return serials[0]


SERIAL = detect_serial()


def screen_geometry():
    """返回 (宽, 高)，用于推算状态栏与导航栏的裁切高度。"""
    out = subprocess.run(
        [ADB, "-s", SERIAL, "shell", "wm", "size"],
        capture_output=True, text=True,
    ).stdout
    m = re.search(r"(\d+)x(\d+)", out)
    if not m:
        return 1080, 1920
    return int(m.group(1)), int(m.group(2))


SCREEN_W, SCREEN_H = screen_geometry()
# 状态栏高度按屏幕比例推算：1080p 约 72px，1260 宽约 119px。
# 导航栏同理，取其高度的约 3%。
STATUS_H = round(SCREEN_W * 119 / 1256)
NAV_START = SCREEN_H - round(SCREEN_H * 78 / 2760)



def adb(*args, timeout=60):
    return subprocess.run(
        [ADB, "-s", SERIAL] + list(args),
        capture_output=True, text=True, timeout=timeout,
    )


def dump_ui():
    adb("shell", "uiautomator", "dump", "/sdcard/_ui.xml")
    adb("pull", "/sdcard/_ui.xml", LOCAL_TMP)
    adb("shell", "rm", "-f", "/sdcard/_ui.xml")
    with open(LOCAL_TMP, encoding="utf-8") as f:
        return f.read()


def parse_nodes(xml_text):
    """返回 [(text, desc, bounds_tuple, clickable)]"""
    nodes = []
    for m in re.finditer(r"<node\b[^>]*>", xml_text):
        tag = m.group(0)

        def g(k):
            r = re.search(r'\b' + k + r'="([^"]*)"', tag)
            return r.group(1) if r else ""

        b = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', tag)
        if not b:
            continue
        nodes.append((
            g("text"), g("content-desc"),
            (int(b.group(1)), int(b.group(2)), int(b.group(3)), int(b.group(4))),
            g("clickable") == "true",
        ))
    return nodes


def find(text, exact=True):
    nodes = parse_nodes(dump_ui())
    for t, d, b, c in nodes:
        if (t == text) if exact else (text in t):
            return b, c
    # 再按 content-desc 找
    for t, d, b, c in nodes:
        if d and ((d == text) if exact else (text in d)):
            return b, c
    return None, None


def tap_bounds(b):
    x = (b[0] + b[2]) // 2
    y = (b[1] + b[3]) // 2
    adb("shell", "input", "tap", str(x), str(y))
    return x, y


def shot(outdir, name):
    os.makedirs(outdir, exist_ok=True)
    adb("shell", "screencap", "-p", "/sdcard/_s.png")
    raw = os.path.join(outdir, "_raw.png")
    adb("pull", "/sdcard/_s.png", raw)
    adb("shell", "rm", "-f", "/sdcard/_s.png")

    out = os.path.join(outdir, name)
    ps = f'''
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::FromFile("{raw}")
$rect = New-Object System.Drawing.Rectangle 0, {STATUS_H}, $src.Width, ({NAV_START} - {STATUS_H})
$dst = $src.Clone($rect, $src.PixelFormat)
$dst.Save("{out}", [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose(); $src.Dispose()
'''
    psf = os.path.join(outdir, "_crop.ps1")
    with open(psf, "w", encoding="utf-8-sig") as f:
        f.write(ps)
    subprocess.run(["powershell", "-NoProfile", "-File", psf], capture_output=True)
    os.remove(psf)
    if os.path.exists(raw):
        os.remove(raw)
    size = os.path.getsize(out) if os.path.exists(out) else 0
    print(f"  {name}  {size//1024} KB")
    return out


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    cmd = sys.argv[1]

    if cmd == "find":
        b, c = find(sys.argv[2], exact=False)
        print(f"{b}  clickable={c}" if b else "未找到")
    elif cmd == "tap":
        b, c = find(sys.argv[2])
        if not b:
            b, c = find(sys.argv[2], exact=False)
        if b:
            x, y = tap_bounds(b)
            print(f"已点击 {sys.argv[2]!r} @ ({x},{y})  bounds={b}")
        else:
            print(f"未找到 {sys.argv[2]!r}")
    elif cmd == "texts":
        for t, d, b, c in parse_nodes(dump_ui()):
            if t or d:
                print(f"  {t!r:28} desc={d!r:14} click={c} {b}")
    elif cmd == "shot":
        shot(sys.argv[2], sys.argv[3])
    else:
        print(__doc__)


if __name__ == "__main__":
    main()
