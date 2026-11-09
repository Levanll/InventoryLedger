"""统一截图：裁掉状态栏与导航栏，保证所有图风格一致。

用法：python scripts/shoot.py <场景名> <输出文件名>
由外部通过 adb 完成界面操作，本脚本只负责「抓图 + 裁切」。
"""
import os
import subprocess
import sys
import time

ADB = r"F:\android-dev\sdk\platform-tools\adb.exe"
SERIAL = "AS6V026716000310"
DEVICE_TMP = "/sdcard/_shot.png"

# 从 dumpsys 读到的精确值（1256x2760 屏幕）
STATUS_H = 119
NAV_START = 2682


def shoot(out_path, wait=0.0):
    if wait > 0:
        time.sleep(wait)

    def run(args):
        return subprocess.run(args, capture_output=True, text=True)

    run([ADB, "-s", SERIAL, "shell", "screencap", "-p", DEVICE_TMP])
    tmp_local = out_path + ".raw.png"
    run([ADB, "-s", SERIAL, "pull", DEVICE_TMP, tmp_local])
    run([ADB, "-s", SERIAL, "shell", "rm", "-f", DEVICE_TMP])

    # 裁切：去掉顶部状态栏、底部导航栏
    try:
        from PIL import Image
        use_pil = True
    except ImportError:
        use_pil = False

    if use_pil:
        img = Image.open(tmp_local)
        cropped = img.crop((0, STATUS_H, img.width, NAV_START))
        cropped.save(out_path, "PNG", optimize=True)
        os.remove(tmp_local)
        print(f"  已裁切: {out_path}  {cropped.width}x{cropped.height}")
    else:
        # 无 PIL：调用 .NET 通过 pwsh 裁切
        ps = f'''
Add-Type -AssemblyName System.Drawing
$src = [System.Drawing.Bitmap]::FromFile("{tmp_local}")
$rect = New-Object System.Drawing.Rectangle 0, {STATUS_H}, $src.Width, ({NAV_START} - {STATUS_H})
$dst = $src.Clone($rect, $src.PixelFormat)
$dst.Save("{out_path}", [System.Drawing.Imaging.ImageFormat]::Png)
$dst.Dispose(); $src.Dispose()
'''
        ps_file = out_path + ".crop.ps1"
        with open(ps_file, "w", encoding="utf-8-sig") as f:
            f.write(ps)
        r = subprocess.run(["powershell", "-NoProfile", "-File", ps_file],
                           capture_output=True, text=True)
        os.remove(ps_file)
        if os.path.exists(tmp_local):
            os.remove(tmp_local)
        if r.returncode != 0:
            print(f"  裁切失败: {r.stderr}")
        else:
            print(f"  已裁切: {out_path}")


if __name__ == "__main__":
    if len(sys.argv) < 3:
        print("用法: python shoot.py <输出目录> <文件名>")
        sys.exit(1)
    outdir, name = sys.argv[1], sys.argv[2]
    os.makedirs(outdir, exist_ok=True)
    shoot(os.path.join(outdir, name))
