"""按 LanPulse 格式重写三个 Release 的描述。

格式要点（从 LanPulse v1.2.1 学到）：
  1. 中文段用 `## 本次更新` 开头，英文段用 `## What's new`
  2. 两段之间用 `---` + HTML 注释分隔
  3. 两段章节完全对称：新增/改进/修复/下载 ↔ Added/Improvements/Fixes/Downloads
  4. 每段都自带下载表与安装提示，不共享
  5. 段首一段话概述，不堆要点
"""

import json
import os
import subprocess
import sys

TOKEN_FILE = r"D:\Documents\deepseek-harness\default-workspace\lanpulse\.gh_token"

DIVIDER = """
---

<!-- English version below / 以下为英文版 -->
"""


def post(method, url, payload, token):
    """用 UTF-8 字节发请求，避免 PowerShell 破坏中文"""
    import urllib.request

    data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        url, data=data, method=method,
        headers={
            "Authorization": "Bearer " + token,
            "Accept": "application/vnd.github+json",
            "Content-Type": "application/json; charset=utf-8",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.status, r.read().decode("utf-8")


# ==========================================================
# v1.2.0
# ==========================================================

V120_ZH = """## 本次更新

库存列表支持两级分类折叠，并新增一条可拖动的滚动指示条。
商品多的时候能先收起无关分类，找东西不用一路滚。

> 🌐 **项目主页**：https://levanll.github.io/InventoryLedger/ —— 中英双语，含截图与安装说明。
> 站点独立于安装包部署，不出现在下面的版本内容里。

### 新增

**两级分类折叠**

一级与二级分类都能折叠，整行可点，不要求精确点中箭头。
折叠后右侧显示「已折叠」与商品数，收起也知道里面有多少。

二级分类的标题原本用浅底配灰字，实测对比度只有一级的一半，
在长列表里会被当成普通分隔线而忽略。现在给二级加了左侧主色竖条作为锚点，
文字提到与一级接近的亮度——仍是「次级」，但不会再被跳过。

**滚动指示条**

列表右缘常驻一条指示器，滑块长度按可见比例计算，能看出自己在整个列表的位置。
可以直接拖动：按下时变粗到 7dp 并加深，拖到底就是列表底。

**商品行跟随二级缩进**

商品卡片按所属二级分类缩进。此前商品与一级标题左对齐，
看起来像「属于整条数码配件」而不是「属于线材」。

### 改进

**二级分类的视觉权重**

原来二级标题的底色透明度是 0.25、文字用灰色，在深色背景下几乎融入背景。
现在底色提到 0.35、文字提到 0.88 不透明度、字重改为 Medium、行高 38→40dp，
并加了 3×16dp 的主色竖条。

### 修复

**分类条从不显示**

统计一级分类的 SQL 用商品的末级分类 id 去匹配一级分类 id，
但商品永远指向最末级（如「T恤」），两者不可能相等，查询恒返回空。
改用递归 CTE 把商品沿 parent_id 向上回溯到所属一级分类再聚合。

**侧边条拖动卡顿**

原实现每收到一次拖动事件就启动一个协程，并实时读取当前索引换算目标位置，
形成正反馈抖动。改为拖动起点做一次快照、用单个协程消费目标索引、
Channel 用 CONFLATED 只保留最新值。

**侧边条拖不到底**

拖动距离与条目索引量纲错配，算出的索引到不了最大值。
改为用滑块行程映射，滑块走完即列表到底。

**侧边条上下留白不等**

底部误用了列表的 96dp 内容留白（那个留白是给「最后一项」能滚到 TabBar 上方用的），
导致滑块悬在半空。改为上下各留 10dp。

**列表项频繁重建**

item key 里带了 `updatedAt`，每次加减数量都让 Compose 认为这是另一个条目，
销毁节点重建，造成卡顿。改为只用 `id`。

### 下载

| 文件 | 说明 |
|---|---|
| `InventoryLedger-1.2.0.apk` | 直装 APK，2.60 MB |
| `SHA256SUMS.txt` | 文件校验值，可自行核对完整性 |

签名与 v1.1.0 相同，可直接覆盖升级，库存与流水完整保留。

首次安装会看到「未知来源」提示，在设置中允许来自此来源即可。
"""

V120_EN = """## What's new

The inventory list now collapses at two levels, and the right edge carries a draggable scroll
indicator. With a long list you can fold away the categories you are not looking at instead of
scrolling past them.

> 🌐 **Official site**: https://levanll.github.io/InventoryLedger/en/ — bilingual, with screenshots and install instructions.
> The site is deployed separately from the package and is not part of the version content below.

### Added

**Two-level category collapsing**

Both level-1 and level-2 categories collapse, and the whole row is tappable — there is no need to
hit the arrow precisely. A collapsed group shows "collapsed" and its item count, so you still know
what is inside.

Level-2 headers previously used a faint background with grey text. Measured contrast was half that
of level-1, and in a long list they read as ordinary separators and got skipped. Each level-2
header now carries a primary-colour bar on its left as an anchor, and its text is brightened to
near level-1 — still secondary, but no longer invisible.

**Scroll indicator**

A persistent indicator on the right edge, with the thumb sized by visible proportion so you can
tell where you are in the list. It is draggable: it thickens to 7dp and darkens while held, and
dragging it to the end reaches the bottom of the list.

**Item rows follow level-2 indentation**

Item cards are indented by their level-2 category. Previously they left-aligned with the level-1
header, which made them look like they belonged to "Digital Accessories" as a whole rather than to
"Cables".

### Improvements

**Visual weight of level-2 headers**

Background opacity went from 0.25 to 0.35, text from grey to 0.88 opacity, weight to Medium, row
height 38 to 40dp, plus a 3x16dp primary-colour bar. On a dark background the old version nearly
merged with the surface.

### Fixes

**Category chips never appeared**

The SQL that counted level-1 categories matched each item's leaf-category id against level-1 ids.
Items always point at the leaf category (such as "T-Shirts"), so the two could never be equal and
the query always returned nothing. Replaced with a recursive CTE that walks each item up through
`parent_id` to its level-1 category before aggregating.

**Janky indicator dragging**

The old implementation launched a coroutine for every drag event and re-read the current index to
compute the target, which produced a feedback oscillation. It now snapshots on drag start, consumes
targets in a single coroutine, and uses a CONFLATED channel so only the latest value survives.

**Indicator could not reach the bottom**

Drag distance and item-index units were mismatched, so the computed index never reached its
maximum. The mapping now uses the thumb's travel; when the thumb reaches the end, the list is at
the bottom.

**Uneven indicator margins**

The bottom margin mistakenly reused the list's 96dp content padding (which exists so the last item
can scroll above the tab bar), leaving the thumb hanging mid-air. It is now 10dp on both ends.

**Frequent row rebuilds**

The item key included `updatedAt`, so every quantity change made Compose treat the row as a
different item, destroying and rebuilding the node. The key is now just `id`.

### Downloads

| File | Description |
|---|---|
| `InventoryLedger-1.2.0.apk` | Installable APK, 2.60 MB |
| `SHA256SUMS.txt` | Checksums for manual verification |

Signed with the same key as v1.1.0, so an in-place upgrade keeps inventory and records intact.

On first install Android will warn about an unknown source; allow it for your file manager or
browser.
"""

# ==========================================================
# v1.1.0
# ==========================================================

V110_ZH = """## 本次更新

界面支持英文，并修复一批会影响日常使用的缺陷。首次加入语言切换，选择会记住。

> 🌐 **项目主页**：https://levanll.github.io/InventoryLedger/ —— 中英双语，含截图与安装说明。
> 站点独立于安装包部署，不出现在下面的版本内容里。

### 新增

**中英双语**

库存页与统计页的标题栏都有切换入口。切换即时生效，不重启应用、不丢失当前页面状态。

实现上没有走系统资源方案（`values-en/strings.xml`）：那需要引入 AppCompat 才支持运行时切换，
而且切换后必须重建 Activity，搜索关键词、滚动位置、打开的弹窗都会丢失。
这里用 Compose 的可观察状态承载语言，切换时相关组件精确重组。

覆盖范围不只是页面标题：约 130 处界面文案、80 个预置分类名、9 个单位、月份标签、
明细时间格式、约 20 条操作提示全部跟随。

**语言选择持久化**

选择写入本地存储，冷启动保持上次选择。1.0.0 没有这个机制，重启后会回落中文。

### 改进

**分类名与单位的翻译边界**

分类名映射只覆盖 80 个预置分类，用户自建的分类保持原样——系统没有依据去翻译用户起的名字。

单位在数据库里仍然存中文（「件」「盒」「毫升」），只在显示时翻译。
原因是步进算法按中文判定（「克」「毫升」走 10，其余走 1），
改成英文存库会让算法和全部历史数据失配。

### 修复

**保质期区块在英文模式下仍是中文**

预设按钮显示「7 天」、到期预测显示「预计 … 到期（还剩 N 天）」，是漏接的文案。

**统计页在英文模式下仍是中文**

月份标签、`全部流水（21 条）`、`入库 6 · 出库 0`、`结存 6` 都没有翻译。

**过期弹窗的分类路径未翻译**

英文模式下弹窗里的 `美妆个护 / 护肤 / 精华` 仍是中文。

**操作提示只有中文**

撤销、删除、条码改名冲突等约 20 条 Toast 只有中文，英文用户看不到发生了什么。

**移除了从未使用的依赖**

`zxing-android-embedded` 自带一个 Activity 与旧版 Camera API，与本项目的 Compose 全屏扫码页
重复，且代码中从未调用。移除后 APK 反而小了 0.05 MB。

### 下载

| 文件 | 说明 |
|---|---|
| `InventoryLedger-1.1.0.apk` | 直装 APK，2.60 MB |
| `SHA256SUMS.txt` | 文件校验值，可自行核对完整性 |

签名与 v1.0.0 相同，可直接覆盖升级，库存与流水完整保留。
"""

V110_EN = """## What's new

The interface now supports English, along with a batch of fixes for problems that affected daily
use. This is the first release with a language switch, and the choice is remembered.

> 🌐 **Official site**: https://levanll.github.io/InventoryLedger/en/ — bilingual, with screenshots and install instructions.
> The site is deployed separately from the package and is not part of the version content below.

### Added

**Chinese and English UI**

Both the Inventory and Stats headers carry a language switch. It takes effect immediately without
restarting the app and without losing the current screen state.

The implementation deliberately avoids the system resource approach (`values-en/strings.xml`): that
requires AppCompat for runtime switching, and it must recreate the Activity afterwards, which loses
the search keyword, scroll position and any open dialog. Language is held in Compose observable
state instead, so only the affected composables recompose.

The change is not limited to page titles: around 130 interface strings, 80 preset category names,
9 units, month labels, detail timestamps and about 20 toast messages all follow.

**Persisted language choice**

The selection is written to local storage and restored on cold start. Version 1.0.0 had no such
mechanism and reverted to Chinese on restart.

### Improvements

**Where translation stops**

Category-name mapping covers only the 80 preset categories; user-created categories are left as
typed, because there is no basis for translating a name the user invented.

Units remain Chinese in the database ("件", "盒", "毫升") and are translated only for display. The
step algorithm decides by Chinese unit ("克" and "毫升" step by 10, everything else by 1), so storing
English would break both the algorithm and all historical data.

### Fixes

**Expiry section stayed Chinese in English mode**

Preset buttons showed "7 天" and the prediction showed "预计 … 到期（还剩 N 天）" — strings that had
simply been missed.

**Stats page stayed Chinese in English mode**

Month labels, "全部流水（21 条）", "入库 6 · 出库 0" and "结存 6" were untranslated.

**Category path in the expired dialog was untranslated**

In English mode the dialog still showed "美妆个护 / 护肤 / 精华".

**Toast messages were Chinese only**

About 20 messages — undo, delete, barcode rename conflicts and others — had no English, so English
users could not see what had happened.

**Removed an unused dependency**

`zxing-android-embedded` bundles an Activity and the legacy Camera API, duplicating this project's
Compose-based full-screen scanner, and was never called. Removing it shrank the APK by 0.05 MB.

### Downloads

| File | Description |
|---|---|
| `InventoryLedger-1.1.0.apk` | Installable APK, 2.60 MB |
| `SHA256SUMS.txt` | Checksums for manual verification |

Signed with the same key as v1.0.0, so an in-place upgrade keeps inventory and records intact.
"""

# ==========================================================
# v1.0.0
# ==========================================================

V100_ZH = """## 本次更新

首个公开版本，仅中文界面。

> 🌐 **项目主页**：https://levanll.github.io/InventoryLedger/ —— 中英双语，含截图与安装说明。
> 站点独立于安装包部署，不出现在下面的版本内容里。

### 核心能力

**三级分类树**

80 个预置分类（7 一级 + 20 二级 + 53 三级）。排序用递归 CTE 生成路径串
（如 `002服装/001上装/001T恤`），字典序天然等于先序 DFS。
直接用 `ORDER BY sort` 会让二级分类（sort 11、12）插在一级（10、20）之间，层级就乱了。

**保质期与组内排序**

默认关闭，开启后填生产日期与保质期天数，到期日自动计算。
排序是三级键：分类树 → 有保质期优先 → 剩余天数升序。
分类必须是第一键——把保质期放首位会让一件快过期的牛奶跳到列表最顶端，
把 T恤分组拆散，找东西反而更慢。

**启动过期提醒**

进入应用即弹窗列出全部已过期商品，带分类路径与过期天数，按过期最久的排前。

**扫码入库**

CameraX 预览 + ZXing 解码，不依赖 GMS / ML Kit，HarmonyOS 等无 Google 服务的设备同样可用。

**条码商品查询**

三级数据源，本地优先：本地自学习库 → 已入库商品 → Open Food Facts / go-upc。
查到的信息作为建议卡片展示，用户点「采用」才写入表单。

**数量就地增减与撤销**

列表每行右侧直接 `−/+`，减到 0 弹窗询问是否删除。
流水只增不删，撤销写入反向补偿记录，历史行数值永不修改。

**月度统计**

月度库存量与月度消耗两个视角，支持切换月份、下钻到单商品流水。

### 下载

| 文件 | 说明 |
|---|---|
| `InventoryLedger-1.0.0.apk` | 直装 APK，2.64 MB |
| `SHA256SUMS.txt` | 文件校验值，可自行核对完整性 |

后续版本 **v1.2.0** 增加了中英双语、两级折叠与滚动指示条，建议优先使用新版。
签名相同，可直接覆盖升级，数据保留。
"""

V100_EN = """## What's new

The first public release. Chinese interface only.

> 🌐 **Official site**: https://levanll.github.io/InventoryLedger/en/ — bilingual, with screenshots and install instructions.
> The site is deployed separately from the package and is not part of the version content below.

### Core capabilities

**Three-level category tree**

80 preset categories (7 level-1, 20 level-2, 53 level-3). Ordering uses a recursive CTE that builds
path strings such as `002Clothing/001Tops/001T-Shirts`, whose lexical order equals pre-order DFS.
Using `ORDER BY sort` directly would interleave level-2 categories (sort 11, 12) among level-1 ones
(10, 20) and scramble the hierarchy.

**Expiry with in-group sorting**

Off by default. Enter a production date and shelf life, and the expiry date is computed. Sorting
uses three keys: category tree, then has-expiry first, then ascending remaining days. Category must
be the first key — putting expiry first flings a nearly-expired carton of milk to the very top and
splits the T-shirt group apart, which makes finding things slower, not faster.

**Expired summary on launch**

Opening the app lists every expired item with its category path and days overdue, longest overdue
first.

**Barcode scanning**

CameraX preview plus ZXing decoding, with no dependency on GMS or ML Kit, so devices without Google
services — HarmonyOS among them — work the same.

**Product lookup**

Three-tier source, local first: the self-learned cache, then existing items, then Open Food Facts
and go-upc. Results appear as a suggestion card and are written only when you tap "Use".

**Inline quantity stepper and undo**

Tap `−/+` on each row; reaching zero prompts to delete. Records are append-only; undo writes a
reversing entry and never mutates historical values.

**Monthly stats**

Monthly stock and usage views, switchable months, drill-down to per-item records.

### Downloads

| File | Description |
|---|---|
| `InventoryLedger-1.0.0.apk` | Installable APK, 2.64 MB |
| `SHA256SUMS.txt` | Checksums for manual verification |

A later release, **v1.2.0**, adds a bilingual UI, two-level collapsing and a scroll indicator.
Prefer the newer build. It uses the same signing key, so an in-place upgrade keeps your data.
"""


RELEASES = [
    ("v1.0.0", 439299135, V100_ZH, V100_EN),
    ("v1.1.0", 439395101, V110_ZH, V110_EN),
    ("v1.2.0", 439477222, V120_ZH, V120_EN),
]


def main():
    with open(TOKEN_FILE, "r", encoding="utf-8") as f:
        token = f.read().strip()

    for tag, rid, zh, en in RELEASES:
        body = zh + DIVIDER + en
        url = f"https://api.github.com/repos/Levanll/InventoryLedger/releases/{rid}"
        try:
            status, _ = post("PATCH", url, {"body": body}, token)
            print(f"  {tag}: 更新成功 [{status}]  body {len(body)} 字符")
        except Exception as e:
            print(f"  {tag}: 失败 {e}")


if __name__ == "__main__":
    main()
