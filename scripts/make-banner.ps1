# ===========================================================
# 生成 README 首图（hero banner）
#
# 参考 LanPulse 的构图：左侧标题 + 副标题 + 标签组 + 脚注，
# 右侧主题图形，背景深色 + 网格 + 光晕。
#
# 为什么用 PowerShell + System.Drawing：
#   需要渲染中文文字，Python 标准库没有字体能力，
#   而 System.Drawing 能直接用系统已装的微软雅黑。
#
# 输出：
#   docs/images/hero-banner-zh.png  1280x420
#   docs/images/hero-banner-en.png  1280x420
#
# 用法：powershell -File scripts/make-banner.ps1
# ===========================================================

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$outDir = Join-Path $root "docs\images"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir -Force | Out-Null }

$W = 1280
$H = 420

# ---- 配色（与应用主色 #007DFF 一致）----
$cBgTop   = [System.Drawing.Color]::FromArgb(255, 9, 14, 26)
$cBgBot   = [System.Drawing.Color]::FromArgb(255, 6, 10, 20)
$cGrid    = [System.Drawing.Color]::FromArgb(255, 26, 40, 64)
$cBlue    = [System.Drawing.Color]::FromArgb(255, 0, 125, 255)
$cBlueLt  = [System.Drawing.Color]::FromArgb(255, 90, 180, 255)
$cBluePale= [System.Drawing.Color]::FromArgb(255, 160, 215, 255)
$cCyan    = [System.Drawing.Color]::FromArgb(255, 70, 220, 220)
$cWhite   = [System.Drawing.Color]::White
$cGray    = [System.Drawing.Color]::FromArgb(255, 140, 158, 180)

function New-Canvas {
    $bmp = New-Object System.Drawing.Bitmap $W, $H
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::ClearTypeGridFit
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

    # 纵向渐变背景
    $rect = New-Object System.Drawing.Rectangle 0, 0, $W, $H
    $grad = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $cBgTop, $cBgBot, 90)
    $g.FillRectangle($grad, $rect)
    $grad.Dispose()

    # 网格
    $pen = New-Object System.Drawing.Pen $cGrid, 1
    for ($x = 0; $x -lt $W; $x += 40) { $g.DrawLine($pen, $x, 0, $x, $H) }
    for ($y = 0; $y -lt $H; $y += 40) { $g.DrawLine($pen, 0, $y, $W, $y) }
    $pen.Dispose()

    # 左侧压暗，让文字更突出
    $dark = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
        (New-Object System.Drawing.Rectangle 0, 0, 800, $H),
        [System.Drawing.Color]::FromArgb(150, 0, 0, 0),
        [System.Drawing.Color]::FromArgb(0, 0, 0, 0),
        0)
    $g.FillRectangle($dark, 0, 0, 800, $H)
    $dark.Dispose()

    return @{ Bmp = $bmp; G = $g }
}

function Add-ShelfGraphic($g, $cx, $cy) {
    # 右侧光晕
    $glowR = 230
    $glow = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glow.AddEllipse($cx - $glowR, $cy - $glowR, $glowR * 2, $glowR * 2)
    $pgb = New-Object System.Drawing.Drawing2D.PathGradientBrush($glow)
    $pgb.CenterColor = [System.Drawing.Color]::FromArgb(70, 0, 125, 255)
    $pgb.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 0, 125, 255))
    $g.FillPath($pgb, $glow)
    $pgb.Dispose(); $glow.Dispose()

    # 四层货架，从下往上：层板 + 商品块
    $layers = 4
    $layerH = 52
    $baseY = $cy + 92

    for ($i = 0; $i -lt $layers; $i++) {
        $t = $i / ($layers - 1.0)
        $y = $baseY - $i * $layerH
        $halfW = 165 - $i * 12

        # 层板（细长，渐亮）
        $boardCol = [System.Drawing.Color]::FromArgb(
            255,
            [int](40 + 110 * $t),
            [int](110 + 100 * $t),
            [int](200 + 55 * $t))
        $bold = New-Object System.Drawing.SolidBrush $boardCol
        $boardRect = New-Object System.Drawing.RectangleF ($cx - $halfW), $y, ($halfW * 2), 6
        $g.FillRectangle($bold, $boardRect)
        $bold.Dispose()

        # 商品块：数量随层递减
        $count = $layers - $i
        $bw = 30
        $gap = 16
        $total = $count * $bw + ($count - 1) * $gap
        $startX = $cx - $total / 2

        for ($k = 0; $k -lt $count; $k++) {
            $bx = $startX + $k * ($bw + $gap)
            $bh = 26 + ($k % 2) * 10
            $a = [int](90 + 110 * $t)
            $blockCol = [System.Drawing.Color]::FromArgb($a, 150, 210, 255)
            $bb = New-Object System.Drawing.SolidBrush $blockCol
            $r = New-Object System.Drawing.RectangleF $bx, ($y - $bh), $bw, $bh
            $g.FillRectangle($bb, $r)
            $bb.Dispose()
        }
    }
}

function Add-Texts($g, $cfg) {
    $left = 80
    $famBold = New-Object System.Drawing.FontFamily "Microsoft YaHei UI"
    $famReg  = New-Object System.Drawing.FontFamily "Microsoft YaHei UI"

    # 主标题
    $fTitle = New-Object System.Drawing.Font($famBold, 46, [System.Drawing.FontStyle]::Bold)
    $bTitle = New-Object System.Drawing.SolidBrush $cWhite
    $g.DrawString($cfg.Title, $fTitle, $bTitle, $left, 82)
    $fTitle.Dispose()

    # 标题下划线（主色短横）
    $underline = New-Object System.Drawing.SolidBrush $cBlueLt
    $g.FillRectangle($underline, $left, 152, 210, 4)
    $underline.Dispose()

    # 副标题
    $fSub = New-Object System.Drawing.Font($famReg, 21)
    $bSub = New-Object System.Drawing.SolidBrush $cBluePale
    $g.DrawString($cfg.Subtitle, $fSub, $bSub, $left, 176)
    $fSub.Dispose()

    # 功能行
    $fFeat = New-Object System.Drawing.Font($famReg, 16)
    $bFeat = New-Object System.Drawing.SolidBrush $cGray
    $g.DrawString($cfg.Features, $fFeat, $bFeat, $left, 220)
    $fFeat.Dispose()

    # 标签组（描边胶囊）
    $x = $left
    $tagY = 258
    $fTag = New-Object System.Drawing.Font($famReg, 14)
    foreach ($tag in $cfg.Tags) {
        $sz = $g.MeasureString($tag.Text, $fTag)
        $w = $sz.Width + 28
        $h = 34
        $col = if ($tag.Highlight) { $cCyan } else { $cBlueLt }
        $penT = New-Object System.Drawing.Pen $col, 1.5
        $path = New-Object System.Drawing.Drawing2D.GraphicsPath
        $r = $h / 2
        $path.AddArc($x, $tagY, $r * 2, $r * 2, 90, 180)
        $path.AddArc($x + $w - $r * 2, $tagY, $r * 2, $r * 2, 270, 180)
        $path.CloseFigure()
        $g.DrawPath($penT, $path)

        $bT = New-Object System.Drawing.SolidBrush $col
        $g.DrawString($tag.Text, $fTag, $bT, ($x + 14), ($tagY + 7))
        $bT.Dispose(); $penT.Dispose(); $path.Dispose()
        $x += $w + 12
    }
    $fTag.Dispose()

    # 脚注
    $fFoot = New-Object System.Drawing.Font($famReg, 13)
    $bFoot = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 104, 122, 145))
    $g.DrawString($cfg.Footer, $fFoot, $bFoot, $left, 336)
    $fFoot.Dispose()

    # 版本号（右下角）
    $fVer = New-Object System.Drawing.Font($famReg, 12)
    $bVer = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(255, 70, 88, 110))
    $vsz = $g.MeasureString($cfg.Version, $fVer)
    $canvasW = $g.VisibleClipBounds.Width
    $canvasH = $g.VisibleClipBounds.Height
    $g.DrawString($cfg.Version, $fVer, $bVer, ($canvasW - $vsz.Width - 26), ($canvasH - 32))
    $fVer.Dispose()

    $famBold.Dispose(); $famReg.Dispose()
    $bTitle.Dispose(); $bSub.Dispose(); $bFeat.Dispose(); $bFoot.Dispose(); $bVer.Dispose()
}

function Build-Banner($path, $cfg) {
    $c = New-Canvas
    Add-ShelfGraphic $c.G 1010 200
    Add-Texts $c.G $cfg
    $c.Bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $c.G.Dispose()
    $c.Bmp.Dispose()
    Write-Host "  $(Split-Path $path -Leaf)  ${W}x${H}"
}

$zh = @{
    Title    = "INVENTORY LEDGER"
    Subtitle = "库存盘点 · 轻量本地库存管理"
    Features = "扫码识别  ·  保质期追踪  ·  三级分类  ·  月度统计"
    Tags     = @(
        @{ Text = "3 级分类"; Highlight = $false },
        @{ Text = "保质期提醒"; Highlight = $false },
        @{ Text = "条码自学习"; Highlight = $false },
        @{ Text = "中 / 英 双语"; Highlight = $true }
    )
    Footer   = "全本地运行 · 零账号 · 零云同步 · 数据只在本机"
    Version  = "v1.2.0"
}

$en = @{
    Title    = "INVENTORY LEDGER"
    Subtitle = "Lightweight offline inventory for Android"
    Features = "Barcode scan  ·  Expiry tracking  ·  3-level categories  ·  Monthly stats"
    Tags     = @(
        @{ Text = "3-level"; Highlight = $false },
        @{ Text = "Expiry alerts"; Highlight = $false },
        @{ Text = "Barcode learning"; Highlight = $false },
        @{ Text = "ZH / EN"; Highlight = $true }
    )
    Footer   = "Runs fully offline · No account · No cloud sync · Data stays on device"
    Version  = "v1.2.0"
}

Write-Host "生成 hero banner..."
Build-Banner (Join-Path $outDir "hero-banner-zh.png") $zh
Build-Banner (Join-Path $outDir "hero-banner-en.png") $en
Write-Host ""
Write-Host "完成。"
