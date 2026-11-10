# HANDOFF — 项目交接

> 给接手这个项目的下一个 AI 会话 / 开发者。读完这一份就能继续干活，不必翻历史对话。
> 最后更新：2026-11-10，对应 v1.3.0。

---

## 一、项目是什么

**Inventory Ledger（库存盘点）** — Android 原生库存管理 App。

- 仓库：https://github.com/Levanll/InventoryLedger （公开，MIT）
- 包名：`cn.levanll.inventory`
- 语言：Kotlin + Jetpack Compose，全离线，无账号无云同步
- 目标用户：个人 / 家庭 / 小店的库存记录

**它和同类应用的三点定位差异**（不要做成普通库存 App）：

1. **三级分类**（服装 → 上装 → T恤），且列表必须按分类分组
2. **保质期**可选开启，列表内按剩余天数排序
3. **中英双语**，运行时切换

> 这三条是「产品定位」层面的差异，与 README 卖点表（11 项，含扫码、自学习、
> 撤销、统计等能力）和站点首屏卡片（4 张，含「已用完」）不是一个口径。
> 改动时不要互相套数字 —— 定位讲的是「为什么做这个 App」，卖点讲的是「有什么功能」。

---

## 二、当前状态

| 项 | 值 |
|---|---|
| 版本 | **v1.3.0**（versionCode 4） |
| Release | GitHub 上 `v1.3.0` / `v1.2.0` / `v1.1.0` / `v1.0.0` 四个，都有 APK，**全部为中英对照** |
| APK 体积 | 2.61 MB（R8 混淆后） |
| 分支 | `main`，工作区干净 |
| 最近提交 | 见 `git log --oneline -1`；最近一次发版提交为 `ba7f95b`（v1.3.0 文档口径修正）。**不要在这里写死 SHA**，每提交一次就过期 |
| 产品站点 | https://levanll.github.io/InventoryLedger/ ，源为 `main` 的 `/docs`，状态 `built` |
| 站点语言 | `/` 中文，`/en/` 英文 |
| 提交身份 | `InventoryLedger Bot <125116742+Levanll@users.noreply.github.com>` |

### 仓库 About 配置（已设置）

| 字段 | 值 |
|---|---|
| Description | 中文在前、`｜` 分隔、英文在后，与 LanPulse 同构 |
| Website | `https://levanll.github.io/InventoryLedger/` |
| Topics | 16 个：android, kotlin, jetpack-compose, material-design, room, ksp, barcode-scanner, zxing, camerax, inventory-management, expiry-tracking, offline-first, i18n, bilingual, harmonyos, apk |

### 已验证可用（真机实测）

在 **HONOR BKQ-AN00 / Android 17 (SDK 37)** 上验证：

- 启动、入库、列表加减、归零删除
- 保质期设置与临期徽标
- 启动过期弹窗
- 三级分类树与分类管理
- 月度统计与明细下钻
- 中英切换 + 语言持久化（重启后保持）
- 从 v1.0.0 覆盖升级，数据完整保留
- **两级分类折叠**：一级与二级均可独立折叠，商品行跟随二级缩进
- **滚动指示条**：可拖动、按下变粗、能拖到底

在 **HUAWEI HBN-AL80 / HarmonyOS 4.2（Android 12 底座，无 GMS）** 上验证过扫码能读出 `6972046811374`。**这条是关键**：证明不依赖 Google 服务。

### 未验证 / 已知风险

- **扫码功能在荣耀 Android 17 上未实测**（只验证了页面能打开，没在真机前扫过条码）。下一步若要动扫码代码，先在两台设备都测一遍
- 分类名英文映射覆盖 80 个预置分类，**用户自建分类不翻译**（有意为之，见「设计决策」）
- 条码查询对国产 69 码覆盖约 60–70%
- 站点未在移动端真机验证（仅桌面 Chrome 1280 / 2560 宽）

---

## 三、环境（这台机器上的实际路径）

**所有工具必须装在 F: 盘，不能用 C:。**

| 用途 | 路径 |
|---|---|
| Android SDK | `F:\android-dev\sdk` |
| Gradle 发行版 | `F:\android-dev\gradle-dist\gradle-8.9` |
| GRADLE_USER_HOME | `F:\android-dev\gradle-home` |
| JDK | `C:\Program Files\Java\jdk-17` |
| Python | `D:\python\python.exe` |
| 项目根 | `D:\Documents\deepseek-harness\default-workspace\inventory-android` |
| 归档目录 | `F:\deepseek-harness\_archive\` |

### 构建命令

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:ANDROID_HOME = "F:\android-dev\sdk"
$env:GRADLE_USER_HOME = "F:\android-dev\gradle-home"
cd "D:\Documents\deepseek-harness\default-workspace\inventory-android"
& "F:\android-dev\gradle-dist\gradle-8.9\bin\gradle.bat" assembleRelease --no-daemon --console=plain
```

**踩过的坑：**
- `gradle.properties` 若带 BOM，`org.gradle.jvmargs` 会失效，堆内存停在 512MB 导致 GC 抖动构建失败。写文件必须用 `New-Object System.Text.UTF8Encoding($false)`（无 BOM）
- 改完版本号后 `assembleRelease` **不会重建 debug APK**，要单独 `assembleDebug`。曾因此误判「版本号没生效」

### 签名（极其重要）

```
keystore/  release.jks  +  keystore.properties
别名：inventory
密码：见 keystore.properties 的 storePassword / keyPassword（两者相同）
```

`keystore/` **已在 .gitignore 排除**，只存在于本地。

**密码不写进任何文档或提交。** 曾把明文密码写在 HANDOFF 里并推送到公开仓库，
2026-11-10 已通过 `keytool -storepasswd` 更换密码（证书指纹不变，已安装用户仍可覆盖升级）。
以后需要交接密码时，走私下渠道，不要落到仓库里。

**这个文件和密码丢了，已安装用户就永远收不到更新**（Android 只允许同签名覆盖安装）。任何情况下不要重新生成 keystore。

证书指纹（改密码不会变，可用来验证 keystore 是否被替换）：

```
SHA-256: 8A:15:BE:DB:28:F4:B4:5F:EC:81:51:A8:A0:17:C2:A4:AE:91:FE:E4:2B:04:D1:5B:2D:3C:4A:82:26:25:1F:BD
DN:      CN=Levanll, OU=Personal, O=Levanll, L=Unknown, ST=Unknown, C=CN
有效期至 2126-10-16，RSA 2048，SHA256withRSA
```

### 测试设备

```powershell
$adb = "F:\android-dev\sdk\platform-tools\adb.exe"
& $adb devices
# 当前: AS6V026716000310  HONOR BKQ-AN00 / Android 17
#       (原设备 33Z0224430004545 HUAWEI HBN-AL80 / HarmonyOS 4.2，可能随时接入)
```

屏幕 1256×2760 @560dpi。**截图要裁掉状态栏**：`0~119px` 是状态栏，`2682~2760px` 是导航栏。

### 辅助脚本

| 脚本 | 用途 |
|---|---|
| `scripts/make_icon.py` | 生成应用图标。纯 Python 写 PNG，**不依赖 Pillow**。改几何/配色后重跑即可 |
| `scripts/make-banner.ps1` | 生成 README 与站点共用的首图（1280×420） |
| `scripts/uihelp.py` | 按文字定位元素并点击（`tap "知道了"` / `find "EN"` / `texts` / `shot 目录 文件名`）。**避免靠记忆坐标点击**。设备序列号自动探测，多台时用 `ADB_SERIAL` 指定 |
| `scripts/shoot.py` | 抓图并裁掉状态栏/导航栏 |
| `scripts/seed_demo.py` | 灌演示数据用于站点截图。设备无 sqlite3，走「base64 拉库 → 本地改 → dd 推回」，需先装 debug 包 |
| `scripts/create_release.py` | 创建 GitHub Release 并上传 APK。中英对照描述，UTF-8 字节发送 |
| `scripts/update_releases.py` | 批量更新既有 Release 的描述文案 |

---

## 四、架构（务必先看这段再改代码）

```
MainActivity
  └─ I18n.init → InventoryTheme → MainScreen
       ├─ InventoryScreen（库存 Tab）
       ├─ StatsScreen（统计 Tab）+ CategoryManageScreen 覆盖
       └─ InboundScreen 全屏浮层 + ScanScreen 全覆盖
              ↓ StateFlow
         MainViewModel（单一状态源）
              ↓
         领域层：InventoryService / ClassifyService / CategoryService
                 StatsService / BarcodeLookupService
              ↓
         Room（6 张表，version = 4）
```

### 两条硬边界（改代码时不许破坏）

1. **所有写操作必须走 `db.withTransaction {}`**
   入库、加减、撤销、分类变更、条码记忆，全部包在事务里。任一失败整体回滚。

2. **`stock_record` 只增不删，撤销写补偿记录**
   撤销不是 UPDATE 或 DELETE 历史行，而是：插入一条 `reason='REVERT'` 的反向记录，再把原记录的 `reverted_by` 指向新记录 id。
   这保证历史可追溯，且月度统计天然正确（撤销出库 = 消耗被抵消）。

### 三级分类排序（最容易改错的地方）

**不能用 `ORDER BY sort`**。`sort` 值 10/11/12/20/21 会让二级分类插在一级之间，层级错乱。

正确做法是递归 CTE 生成路径串再排序：

```sql
WITH RECURSIVE tree(id, name, path) AS (
    SELECT id, name, printf('%05d', sort) || '/' || name
    FROM category WHERE parent_id IS NULL
    UNION ALL
    SELECT c.id, c.name, tree.path || '/' || printf('%05d', c.sort) || '/' || c.name
    FROM category c JOIN tree ON c.parent_id = tree.id
)
SELECT * FROM tree ORDER BY path ASC
```

路径串 `00010服装/00011上装/00111T恤` 的字典序 == 先序 DFS。`printf('%05d')` 的零填充是关键，否则 `sort=100` 会排在 `sort=20` 前。

见 `CategoryDao.listForPick()` / `listAllWithUsage()`。

### 库存列表排序（三级键，顺序不能错）

```sql
ORDER BY
  c1.sort, c2.sort, c3.sort, category_id,   -- 1. 分类分组
  has_expiry ASC,                            -- 2. 组内有保质期优先
  expiry_at ASC,                             -- 3. 按到期时间升序
  updated_at DESC
```

**分类必须是第一键**。早期版本把保质期放首位，结果一件快过期的牛奶跑到列表最顶端，把 T恤分组拆散。用户找东西更慢。

见 `ItemDao.search()`。

### 保质期不落库

只存 `production_date` + `shelf_life_days`，到期时间由 SQL 现算。
若落库，改保质期时必须同步改到期日，两处不一致会产生难查的 bug。

---

## 五、国际化（v1.1.0 新增，占比很大）

### 为什么不用系统资源方案

没用 `values-en/strings.xml` + `stringResource()`，因为：
1. 运行时切换要 AppCompat 的 `setApplicationLocales`，本项目基于 `ComponentActivity`
2. 原生方案切换要 recreate，会丢页面状态
3. Compose 的 `mutableStateOf` 天然可观察，切了就重组

### 用法

```kotlin
I18n.s("库存", "Inventory")          // 中文在前，英文在后
CategoryNames.of("服装")             // 分类名 → Clothing
CategoryNames.ofPath("服装 / 上装")   // 路径整体翻译
UnitNames.of("件")                   // 单位 → pcs
```

### 四条重要约定

| 属于 | 处理 |
|---|---|
| 界面文案 | 翻译 |
| 80 个预置分类名 | 翻译（`CategoryNames.MAP`） |
| 单位 | **显示时翻译，数据库仍存中文** |
| 用户数据（商品名/规格/备注/自建分类名） | **不翻译** |

**单位为什么存中文**：`Constants.stepOfUnit()` 按中文判定步进（"克"/"毫升" 走 10，其余走 1）。改成英文存库会让这个判定和历史数据全部失配。

**用户数据为什么不翻译**：机器翻译会把 "Milk" 变成"牛奶"，用户按英文搜反而搜不到。保持原样是唯一正确的选择。

**第四类容易漏：落库的备注文案**（`stock_record.note`）既不是纯界面文案、也不该翻成用户语言后存死。
约定是存稳定的英文键，显示时经 `RecordNotes.of()` 翻译，详见第十二·五节。

### 日期也要本地化

只换 pattern 不换 Locale 不够 —— `MMM` 在中文 Locale 下仍输出"11月"。

```kotlin
val pattern = if (I18n.lang == Lang.ZH) "M月d日 HH:mm" else "MMM d, HH:mm"
val locale = if (I18n.lang == Lang.ZH) Locale.getDefault() else Locale.ENGLISH
```

见 `StatsScreen.fmtDateTime()`、`YearMonth.label()`。

### 语言必须持久化

第一版没存盘，冷启动回落到中文，英文用户每次都要重切。现在写 SharedPreferences（`i18n` / `lang`），在 `MainActivity.onCreate` 里于 `setContent` **之前**调 `I18n.init(this)`，否则首帧会闪一下中文。

---

## 六、数据库

`version = 4`，6 张表：

| 表 | 职责 |
|---|---|
| `item` | 商品（含 `expiry_enabled` / `production_date` / `shelf_life_days` / `deleted_at`） |
| `stock_record` | 流水，只增不删（`reverted_by` 指向补偿记录） |
| `category` | 分类树（`parent_id` 自关联 + 冗余 `depth` 1/2/3） |
| `category_rule` | 名称→分类规则（`token` 唯一，`source` = system / learned） |
| `barcode_memo` | 条码自学习（`barcode` 唯一） |
| `form_memo` | 表单记忆 key-value |

### 迁移链

- v1：初版，含盘点表 `count_session` / `count_line`
- v2：移除盘点表；`ux_item_barcode` 部分唯一索引改普通索引
- v3：加 `depth` + 三个保质期字段
- v4：加 `barcode_memo`

**改 schema 时注意：**
- `DROP INDEX IF EXISTS` 要放在建表前，否则 Room schema 校验会因未知索引失败
- 加字段必须升级 `version` 并写迁移，不能只改实体
- 种子用 `SeedData.SEED_VERSION` 守卫，只补缺失项，不覆盖用户改动

---

## 七、扫码

用 **ZXing core + CameraX 自建预览**，不依赖 GMS / ML Kit（HarmonyOS 设备没有 Google 服务）。

### 一个致命 bug 的记录（别再犯）

早期手工解析 `ImageProxy` 的 Y 平面，按 `width` 连续读取，忽略了 `rowStride`（每行按 16/32 对齐补齐，通常大于 width）。图像被斜切成垃圾数据，ZXing 在垃圾里凑出**能通过 EAN-8 校验的假码**（真实 `6972046811374` 被读成 `02224020`）。

这种错误比直接失败更危险 —— 它不报错，用户以为扫对了。

**现在用 `ImageProxy.toBitmap()`**，由 CameraX 处理全部平面与跨距。同时在 `ImageAnalysis` 上开启 `.setOutputImageRotationEnabled(true)`。

只启用 EAN_13 / UPC_A / EAN_8 / UPC_E / CODE_128。**放开 QR / DataMatrix 会显著提高误识别率**，入库场景没收益。

解码节流 80ms（约 12 次/秒）。

---

## 八、条码商品查询

三级数据源，**本地优先于联网**：

1. `barcode_memo` 本地自学习（用户亲手确认过，最可靠）
2. `item.barcode` 已入库商品
3. 联网：Open Food Facts（JSON）→ go-upc（HTML 抓取）

### 几条纪律

- **只返回建议，绝不自动写入表单**。第三方数据可能错、可能过时，直接写等于把别人的错误变成用户的库存事实
- **负缓存用 `NotFound` 哨兵对象**。`ConcurrentHashMap` 不允许 null value，存 null 会直接 NPE
- **go-upc 返回的中文可能是乱码**（UTF-8 字节被按 latin-1 解释），需要 `ISO-8859-1 → UTF-8` 往返修复
- 任何网络异常静默返回 null，离线时应用必须照常可用

### 已知覆盖情况

- `6972046811374`（用户的一件商品）**在所有公开库中都查不到**。华为/荣耀能识别是因为 `com.huawei.scanner`（`/system/priv-app/HiVision`）接了商业库，无公开 API
- go-upc 对部分国产码有效：`6902083881405` → 娃哈哈，`6920202888883` → 红牛

---

## 九、文档结构

| 文件 | 内容 |
|---|---|
| `README.md` | 中文主 README，含界面预览 |
| `README_EN.md` | 英文 README，结构与中文对称 |
| `CHANGELOG.md` | 按版本记录，1.3.0 在最上 |
| `SECURITY.md` | 漏洞报告渠道 |
| `HANDOFF.md` | 本文件 |
| `docs/设计方案.md` | 数据模型、事务纪律、分类机制、i18n、迁移链 |
| `docs/页面规格.md` | 线框、控件规格、状态矩阵、语言切换、折叠与滚动条 |
| `docs/index.html` | **产品站点首页（中文）**，Pages 从 `/docs` 发布 |
| `docs/en/index.html` | 产品站点英文页 |
| `docs/style.css` | 站点样式（深色，配色取自应用主色） |
| `docs/images/zh/*.png` | 中文截图 9 张 |
| `docs/images/en/*.png` | 英文截图 9 张 |
| `docs/images/hero-banner-{zh,en}.png` | README 与站点共用的首图 |
| `notes/v*.md` | 各版本的 Release 描述原文（中英对照），`create_release.py` 的输入 |

**文档必须与代码一致。** 曾发现 README 写"4 张核心表"实际 6 张、"7 张表实体"实际 6 张、引用不存在的图片、声称"零额外依赖"却引入了从未使用的 `zxing-android-embedded`。改代码后顺手核对文档。

---

## 十、产品站点（GitHub Pages）

站点源是 `main` 分支的 `/docs` 目录，纯静态 HTML，**不经过 Jekyll 构建**。
已于 2026-11-10 通过 API 启用（`POST /repos/{owner}/{repo}/pages`，body 为
`{"source":{"branch":"main","path":"/docs"}}`），构建状态 `built`。

| 路径 | 说明 |
|---|---|
| https://levanll.github.io/InventoryLedger/ | 中文首页 |
| https://levanll.github.io/InventoryLedger/en/ | 英文首页 |

**启用 Pages 的注意事项**：这个端点第一次尝试失败（返回 400），换用
`Invoke-WebRequest -UseBasicParsing` 并把 body 显式转成 UTF-8 字节后成功。
如果以后需要重建站点，用同样方式，不要用 `Invoke-RestMethod` 直接传对象。

`LanPulse` 是另一个独立站点（`https://levanll.github.io/LanPulse/`），两者互不影响。

### 本地预览

```powershell
# 必须用独立进程，PowerShell 的 Start-Job 会随命令结束而终止
Start-Process -FilePath "D:\python\python.exe" `
  -ArgumentList "-m","http.server","18765","--bind","127.0.0.1","--directory","<项目根>\docs" `
  -WindowStyle Hidden

# 停止（按命令行匹配，不要按 PID 记，因为每次不同）
Get-CimInstance Win32_Process -Filter "Name = 'python.exe'" |
  Where-Object { $_.CommandLine -match '18765' } |
  ForEach-Object { Stop-Process -Id $_.ProcessId -Force }
```

### 截图轮播的关键决策（改之前先读）

截图区是横向缓慢滑动的轮播，**不是九宫格**。手机截图竖长，网格会把每张压小又把页面拉长。

**容器绝不能设 `overflow: hidden`。** 这是踩过的坑：

- 最初用 `overflow: hidden` 横向裁切，再靠纵向内边距留放大余量
- 结果余量只够 1.08 倍，改到 1.5 倍立刻被纵向裁掉一截
- CSS 规定一个方向 `hidden` 时另一个方向不能 `visible`，靠内边距不可能覆盖任意倍数

现做法：`.marquee` 不设任何 `overflow`，横向溢出交给 `html { overflow-x: hidden }`。
**代价是两侧不能做渐隐**（`mask-image` 要求容器形成裁切上下文）。用户已确认接受这个取舍。

悬停放大用 `transform: scale(1.5)`，不用改宽高——改宽高触发 reflow，整个轨道重算，明显卡顿。

### 截图的白底问题

App 截图是纯白（`#FFF`），整站深色，白图直接铺满卡片会像贴上去的一块亮矩形。**不改截图**，用画框解决：

- 卡片底色比页面还深一档（`--panel-deep: #0a1019`）
- 图片四周 6px 内边距，白图不再顶到卡片边缘
- 图片自身圆角 6px + 内阴影，压住边缘的「硬」
- 上下各一条 8px 渐变（顶部 0.22、底部 0.30）软化硬切

---

## 十一、发布流程

```powershell
# 1. 改 app/build.gradle.kts 的 versionCode / versionName
# 2. 更新 CHANGELOG.md、README 里的 APK 文件名与体积
# 3. 构建并实测
& "...gradle.bat" assembleDebug assembleRelease --no-daemon --console=plain
# 4. 装到设备验证（release 版不可调试，run-as 用不了，只能 UI 验证）
adb install -r -t app/build/outputs/apk/release/app-release.apk
# 5. 提交
git add -A && git commit -m "..."
# 6. 推送
git push https://x-access-token:<TOKEN>@github.com/Levanll/InventoryLedger.git HEAD:main
# 7. 创建 Release 并上传 APK（用 GitHub API）
```

### Release 描述必须是中英对照

格式对齐 LanPulse（`scripts/update_releases.py` 里有可复用的实现）：

```
## 本次更新          ← 中文段开头
（一段话概述）

### 新增 / 改进 / 修复 / 下载

---

<!-- English version below / 以下为英文版 -->

## What's new        ← 英文段开头
（对应的英文概述）

### Added / Improvements / Fixes / Downloads
```

**两段章节必须完全对称**，每段自带下载表与安装提示，不共享。
三个版本的 Release 现在都是这个结构，改动时保持。

### 创建 Release 时防乱码

PowerShell 把对象转 JSON 字符串时会破坏中文（变 `?`）。**必须走字节**：

```powershell
$json = $bodyObj | ConvertTo-Json -Depth 5
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
Invoke-WebRequest -Body $bytes -ContentType "application/json; charset=utf-8"
```

更稳的做法是用 Python 的 `json.dumps(ensure_ascii=False).encode("utf-8")`，
见 `scripts/update_releases.py`。

创建后必须验证：`$rel.body -match '\?\?\?'` 为 false。

### token

从 `D:\Documents\deepseek-harness\default-workspace\lanpulse\.gh_token` 读取。**只读，不改该目录任何内容。** 只在推送命令的 URL 里临时使用，**不要写入 git 配置或任何文件**。

这个文件曾被删除过一次导致推送失败。缺失时向用户要新 token，不要试图从别处找凭证。

---

## 十二、库存列表：折叠与滚动指示条（v1.2.0）

这两个功能都在 `InventoryScreen.kt`，样式组件在 `Widgets.kt`。

### 两级折叠

数据结构上，`InventoryUiState.collapsedRoots: Set<Long>` 存的是**已折叠的 id**，不是已展开的。
这样默认全部展开符合直觉，新出现的分类也默认展开。

`toggleGroup(id)` 负责加减集合，一级与二级共用同一个集合——id 全局唯一，不需要分开存。

列表渲染是两层 `groupBy`：

```kotlin
state.items.groupBy { it.catL1 ?: "" }
    .mapValues { (_, rows) -> rows.groupBy { it.catL2 ?: "" } }
```

`items` 已按分类树排好序，`groupBy` 保持插入顺序，所以嵌套两层即得到正确的两级分组。

**关键点：折叠用的是 `catL1Id` / `catL2Id`，不是分类名。**
不同一级下可能存在同名二级（如「服装/配饰」与「日用家居/配饰」），用名字当 key 会串组。
这两个 id 由 `ItemWithCategory` 投影从 SQL 带出（`c1.id AS catL1Id, c2.id AS catL2Id`）。

### 滚动指示条

组件是 `Widgets.kt` 的 `ScrollIndicator`，接 `LazyListState`。几条实测得出的设计：

1. **滑块只在 draw 阶段算**（`drawBehind`）。最初在组合阶段读 `listState.layoutInfo`，
   每滚动一帧都触发重组，这是卡顿主因。
2. **拖动用 `rememberDraggableState` + Channel**，回调里只做整数运算并 `trySend`。
   最初每次拖动事件都 `scope.launch` 一个协程，高频拖动时协程堆积，表现为明显延迟。
   Channel 用 `CONFLATED`，只保留最新目标。
3. **拖动起点做快照**（`thumbStartPx`、`accumPx`）。若边拖边读 `firstVisibleItemIndex`，
   会与自身滚动形成正反馈而抖动。
4. **位置换算用滑块行程**（`usablePx`），不是条目索引。最初的写法拿 `baseIndex` 累加，
   量纲错配，永远到不了底部。
5. **上下留白必须相等**（各 10dp）。底部曾误用列表的 96dp 内容留白
   （那个留白是给最后一项能滚到 TabBar 上方用的），导致滑块悬在半空。

### 分类 chips 曾经完全不显示

`CategoryDao.listRootStats()` 原来的 JOIN 是错的：

```sql
-- 错误：商品的 category_id 指向末级分类（T恤），永远不等于一级分类（服装）的 id
FROM category c JOIN item i ON i.category_id = c.id WHERE c.depth = 1
```

改成递归 CTE 把每个商品沿 `parent_id` 向上回溯到所属一级分类再聚合。
修复前查询恒返回 0 行，修复后正确返回 4 个一级分类、21 个商品。

---

## 十二·五、「已用完」分区与双语补全（v1.3.0）

### 已用完分区

归零弹窗有「删除 / 保留记录」两个选项，但 `ItemDao.search()` 固定带 `WHERE quantity > 0`，
**两者都会让商品立刻从列表消失**，用户看不出差别，「保留记录」形同虚设。

现在：`deleted_at IS NULL AND quantity <= 0` 的商品由 `ItemDao.listUsedUp()` 取出，
渲染成列表底部的可折叠分区（`UsedUpHeader` / `UsedUpRow`，在 `InventoryScreen.kt`）。
展开后每行有「恢复」「删除」。

**踩过的坑**：`adjustQty()` 里先 `refreshInventory()` 再回调 `onZero()`，
所以归零那次刷新查不到刚归零的商品。弹窗被「保留记录」关掉后必须**再刷一次**，
否则用户点了保留看不到任何东西。这个缺陷就是本版本要修的核心问题。

### 双语的三层边界

| 层 | 载体 | 处理 |
|---|---|---|
| 界面文案 | `I18n.s(中文, English)` | 内联，切换即重组 |
| 业务异常 | `AppException(code, 中文, payload, en = 英文)` | `message` 按当前语言动态返回，UI 直接展示 `e.message` |
| 历史数据 | `stock_record.note` | **存稳定英文键**，显示时经 `RecordNotes.of()` 翻译 |

第三层最容易漏。`note` 是落库的历史事实，写入时按当时语言存会导致切语言后新旧记录混杂。
历史数据里已写死的中文（v1.3.0 之前）无法追溯改写，所以 `RecordNotes` 同时登记中文原文与英文键。

**`AppException` 的参数顺序有讲究**：`(code, zh, payload, en)` —— payload 在第 3 位是为了兼容
既有的 `AppException(code, "中文", mapOf(...))` 调用点，英文用具名参数 `en =` 传入。

### R8 规则曾经完全无效

`proguard-rules.pro` 里写的是 `com.example.inventory.**`，实际包名是 `cn.levanll.inventory`。
四条 keep 规则自项目创建起就没生效过，`AppException` 一直被混淆成 `C1.a`。
v1.3.0 修正后，`mapping.txt` 里这三个类保留原名，而未加 keep 的 `CategoryService` 仍是 `B1.l`。
**改完 proguard 规则必须查 mapping.txt 验证，不能只看构建成功。**

---

---
## 十三、设计决策（不要轻易推翻）

| 决策 | 理由 |
|---|---|
| 盘点功能已移除 | 个人场景下逐项核对成本远高于收益，用随时加减替代 |
| 批量出库已移除 | 与列表内加减重复，多一层选择成本 |
| 数据导出不提供 | 保持界面简洁，换机接受重新录入 |
| 云同步不做 | 隐私优先，避免后端维护成本 |
| 系统通知不做 | 需通知权限，过期提醒只在打开应用时出现 |
| 归零不自动删商品 | 误删不可逆，且历史流水要能引用 |
| 三级上限 | 不加限制分类树会无限膨胀 |
| 条码改名不锁死 | 弹出三选项让用户决定条码归属，而不是禁止改名 |
| 折叠用「折叠集合」而非「展开集合」 | 默认全部展开符合直觉，新分类也默认展开 |
| 折叠支持两级 | 只有一级折叠时，二级分类多的分组（如「服装」下 4 个二级）仍然很长 |
| 二级头加主色竖条 | 原本对比度只有一级一半，长列表里被当成普通分隔线而忽略 |
| 商品行跟随二级缩进 | 不缩进会让商品看起来属于一级分类而非二级 |
| 轮播容器不设 overflow | 见「十、产品站点」，靠内边距留余量无法覆盖任意放大倍数 |
| 站点截图不改图 | 白底是 App 本身的浅色模式，改图会失真；用画框适配更诚实 |

**这些都是用户明确拍板或经过实测验证的，改动前先确认。**

---

## 十四、下一步可能的工作

来自 README 的 Roadmap，优先级未定：

- [ ] 条码识别增强（接入商业库，用户自备 Key）
- [ ] 桌面小组件（显示临期数量）
- [ ] 批量编辑（多选改分类）
- [ ] 图片附件（`image_uri` 字段已预留）
- [ ] 深浅色手动切换（当前跟随系统）

### 技术债 / 可改进处

- `InboundScreen.kt` 897 行，是最大的单文件，可拆分
- `MainViewModel.kt` 992 行，职责偏多
- 扫码功能尚未在荣耀设备实测
- 站点未做移动端真机验证，窄屏下轮播与表格的换行未确认
- `test-artifacts/` 约 150 MB，是测试中间产物，已在 gitignore；可清理但不影响功能
- `docs/设计方案.md` 与 `docs/页面规格.md` 尚未补 1.2.0 的折叠与滚动条设计细节
  （HANDOFF 第十二节有实现说明，但设计文档还是空的）

---

## 十五、事故记录（教训）

### 源码毁灭事故

上一轮重构时，脚本里写了：

```powershell
if (Test-Path $newDir) { Remove-Item $newDir -Recurse -Force }
Move-Item $oldDir $newDir
```

因为目标目录已存在，`Remove-Item -Recurse -Force` **删掉了整个 `cn` 树，包含全部 22 个 .kt 源文件**，随后 `Move-Item` 失败。

**标准：在执行任何递归删除前，必须打印解析后的绝对路径和目录内容，确认是目标对象再删。优先移动到归档而不是删除。**

### 提交身份与历史重写

仓库的作者/提交者身份统一为 `InventoryLedger Bot <125116742+Levanll@users.noreply.github.com>`。

**为什么用 noreply 邮箱**：GitHub 的 noreply 格式 `{用户id}+{登录名}@users.noreply.github.com`
能让提交正确关联到账号（头像、贡献图都正常），同时不暴露真实邮箱。

**仓库级身份已设置**，新提交不会再变成别的名字：

```powershell
git config user.name  "InventoryLedger Bot"
git config user.email "125116742+Levanll@users.noreply.github.com"
```

**已做过的历史重写**（2026-11-10）：11 个提交原本是 `DSH <local@dsh>`，用 `filter-branch` 全部改写后强制推送。
连带被改写的 `v1.0.0` 标签也做了 force push，事后清理了 `refs/original`。

**踩坑：`filter-branch --env-filter` 在 PowerShell 里传 here-string 会被拆行**，报 `fatal: bad revision 'Bot`。
正确做法是把 export 语句写成单行、用分号分隔：

```powershell
git filter-branch -f --env-filter "export GIT_AUTHOR_NAME='X'; export GIT_AUTHOR_EMAIL='Y'; export GIT_COMMITTER_NAME='X'; export GIT_COMMITTER_EMAIL='Y'" -- --all
```

**注意**：`filter-branch` 会改写所有引用的提交 hash，必须 `--force` 推送，且会让所有已 clone 的副本失效。
只在确实需要时做，且做完要确认远端分支与标签都已更新。

### 其他值得记的

- 用 PowerShell 的 `System.Drawing` 画图会遇到数组运算语义、`New-Object` 构造器歧义、UTF-8 BOM 三个坑。几何绘制改用 Python 纯计算
- 批量正则替换 Kotlin 源码时，跨行模板字符串与注释会被误伤。要么收紧正则（只匹配单行、不含 `$` 插值），要么逐条 `edit`。**改完必须编译验证**
- adb 传输二进制会损坏，必须 base64 编码后 push，再 `base64 -d`
- `adb shell screencap` 重定向到本地文件会损坏 PNG，必须先存到设备再 `adb pull`
- 坐标点击不可靠（布局会变），用 `scripts/uihelp.py` 按文字定位
- `Start-Job` 起的 HTTP 服务器会随 PowerShell 命令结束而终止，要用 `Start-Process` 起独立进程
- 查端口占用用 `Get-CimInstance Win32_Process` 匹配 `CommandLine`，`Get-NetTCPConnection` 不一定能拿到
- 写 PNG 时 `IHDR` 的颜色类型必须与每行字节匹配：RGB 用类型 2（3 字节/像素），RGBA 用类型 6（4 字节/像素），不匹配会生成打不开的文件
- PowerShell 读取 PNG 的字节偏移容易算错（IHDR 实际从第 16 字节开始），验证图片尺寸优先用 Python 解析
- **启用 Pages 用 `Invoke-WebRequest -UseBasicParsing`**，body 显式转 UTF-8 字节。
  用 `Invoke-RestMethod` 直接传对象会返回 400
- PowerShell 的 `.Replace()` 对含全角标点的长串容易静默不命中，替换后**必须回读文件确认**，
  不能只看命令输出

### 三次判断错误（不是环境问题，是我自己的问题）

**1. 误判 LanPulse 的 Release 没有英文**

只看了 `body` 前 400 字符就下结论说"LanPulse 的 Release 全是中文"。
实际它每个 Release 都是中英对照，英文段在 2000 字符之后，用 `## What's new` 开头。

**教训：判断一个文件/文档的结构，必须读完全文或至少扫一遍所有标题行，不能用开头片段代表整体。**

**2. 把缓存当成渲染缺陷**

浏览器加载 `/` 与 `/en/` 时命中了旧缓存，导致中文页显示英文内容。
在没有排除缓存的情况下，我一度认为是文件被写错了。

**教训：本地静态站点验证时，URL 加 `?v=N` 破缓存，或先确认 `document.documentElement.lang` 与预期一致，再判断内容对错。**

**3. 声称 Pages 需要用户手动启用**

第一次调 `POST /repos/{owner}/{repo}/pages` 返回 400，我判断为 token 权限不足，
让用户去网页手动操作。实际上换用 `Invoke-WebRequest -UseBasicParsing` 加 UTF-8 字节后一次就成功了（201）。

**教训：API 返回 400 是「请求有问题」，401/403 才是「权限有问题」。**
诊断前先分清状态码含义，不要把自己的请求格式错误归因给用户权限。

---

## 十六、给下一个会话的第一件事

```powershell
cd "D:\Documents\deepseek-harness\default-workspace\inventory-android"
git log --oneline -3          # 看最近三个提交，确认在 main 上
git status                    # 确认工作区干净
git config user.name          # 应为 InventoryLedger Bot
Get-ChildItem app\src\main\java\cn\levanll\inventory -Recurse -Filter *.kt | Measure-Object
# 应该是 26 个文件（v1.3.0 起；新增文件时同步这个数字）
```

**不要靠 SHA 核对起点**：每次提交都会让它过期，用 `git log` 看提交标题判断即可。

然后读 `docs/设计方案.md` 和本文件的第十二节（折叠与滚动条），再动手。

**用户偏好**：中文交流、直奔结论、不写客套话、给证据不给承诺、遇到阻塞说清「卡在哪 + 排除了什么 + 需要什么决策」。
