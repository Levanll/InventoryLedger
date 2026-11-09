# HANDOFF — 项目交接

> 给接手这个项目的下一个 AI 会话 / 开发者。读完这一份就能继续干活，不必翻历史对话。
> 最后更新：2026-11-10，对应 v1.1.0。

---

## 一、项目是什么

**Inventory Ledger（库存盘点）** — Android 原生库存管理 App。

- 仓库：https://github.com/Levanll/InventoryLedger （公开，MIT）
- 包名：`cn.levanll.inventory`
- 语言：Kotlin + Jetpack Compose，全离线，无账号无云同步
- 目标用户：个人 / 家庭 / 小店的库存记录

**它和同类应用的三点差异**（不要做成普通库存 App）：

1. **三级分类**（服装 → 上装 → T恤），且列表必须按分类分组
2. **保质期**可选开启，列表内按剩余天数排序
3. **中英双语**，运行时切换

---

## 二、当前状态

| 项 | 值 |
|---|---|
| 版本 | **v1.1.0**（versionCode 2） |
| Release | GitHub 上 `v1.1.0` + `v1.0.0` 两个，都有 APK |
| APK 体积 | 2.60 MB（R8 混淆后） |
| 分支 | `main`，工作区干净 |
| 最近提交 | `04c1cdf v1.1.0：中英双语 + 一批缺陷修复` |

### 已验证可用（真机实测）

在 **HONOR BKQ-AN00 / Android 17 (SDK 37)** 上验证：

- 启动、入库、列表加减、归零删除
- 保质期设置与临期徽标
- 启动过期弹窗
- 三级分类树与分类管理
- 月度统计与明细下钻
- 中英切换 + 语言持久化（重启后保持）
- 从 v1.0.0 覆盖升级，数据完整保留

在 **HUAWEI HBN-AL80 / HarmonyOS 4.2（Android 12 底座，无 GMS）** 上验证过扫码能读出 `6972046811374`。**这条是关键**：证明不依赖 Google 服务。

### 未验证 / 已知风险

- **扫码功能在荣耀 Android 17 上未实测**（只验证了页面能打开，没在真机前扫过条码）。下一步若要动扫码代码，先在两台设备都测一遍
- 分类名英文映射覆盖 80 个预置分类，**用户自建分类不翻译**（有意为之，见「设计决策」）
- 条码查询对国产 69 码覆盖约 60–70%

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
密码：UTJdVqnVhSdTQMiR5JZuKyDM
别名：inventory
```

`keystore/` **已在 .gitignore 排除**，只存在于本地。

**这个文件和密码丢了，已安装用户就永远收不到更新**（Android 只允许同签名覆盖安装）。任何情况下不要重新生成 keystore。

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
| `scripts/uihelp.py` | 按文字定位元素并点击（`tap "知道了"` / `find "EN"` / `texts` / `shot 目录 文件名`）。**避免靠记忆坐标点击** |
| `scripts/shoot.py` | 抓图并裁掉状态栏/导航栏 |

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

### 三条重要约定

| 属于 | 处理 |
|---|---|
| 界面文案 | 翻译 |
| 80 个预置分类名 | 翻译（`CategoryNames.MAP`） |
| 单位 | **显示时翻译，数据库仍存中文** |
| 用户数据（商品名/规格/备注/自建分类名） | **不翻译** |

**单位为什么存中文**：`Constants.stepOfUnit()` 按中文判定步进（"克"/"毫升" 走 10，其余走 1）。改成英文存库会让这个判定和历史数据全部失配。

**用户数据为什么不翻译**：机器翻译会把 "Milk" 变成"牛奶"，用户按英文搜反而搜不到。保持原样是唯一正确的选择。

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
| `CHANGELOG.md` | 按版本记录，1.1.0 在最上 |
| `SECURITY.md` | 漏洞报告渠道 |
| `docs/设计方案.md` | 数据模型、事务纪律、分类机制、i18n、迁移链 |
| `docs/页面规格.md` | 线框、控件规格、状态矩阵、语言切换 |
| `docs/images/zh/*.png` | 中文截图 8 张 |
| `docs/images/en/*.png` | 英文截图 8 张 |

**文档必须与代码一致。** 曾发现 README 写"4 张核心表"实际 6 张、"7 张表实体"实际 6 张、引用不存在的图片、声称"零额外依赖"却引入了从未使用的 `zxing-android-embedded`。改代码后顺手核对文档。

---

## 十、发布流程

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

### 创建 Release 时防乱码

PowerShell 把对象转 JSON 字符串时会破坏中文（变 `?`）。**必须走字节**：

```powershell
$json = $bodyObj | ConvertTo-Json -Depth 5
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
Invoke-WebRequest -Body $bytes -ContentType "application/json; charset=utf-8"
```

创建后必须验证：`$rel.body -match '\?\?\?'` 为 false。

### token

从 `D:\Documents\deepseek-harness\default-workspace\lanpulse\.gh_token` 读取。**只读，不改该目录任何内容。** 只在推送命令的 URL 里临时使用，**不要写入 git 配置或任何文件**。

---

## 十一、设计决策（不要轻易推翻）

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

**这些都是用户明确拍板或经过实测验证的，改动前先确认。**

---

## 十二、下一步可能的工作

来自 README 的 Roadmap，优先级未定：

- [ ] 条码识别增强（接入商业库，用户自备 Key）
- [ ] 桌面小组件（显示临期数量）
- [ ] 批量编辑（多选改分类）
- [ ] 图片附件（`image_uri` 字段已预留）
- [ ] 深浅色手动切换（当前跟随系统）

### 技术债 / 可改进处

- `InboundScreen.kt` 862 行，是最大的单文件，可拆分
- `MainViewModel.kt` 842 行，职责偏多
- 扫码功能尚未在荣耀设备实测
- `test-artifacts/` 151.8 MB，是测试中间产物，已在 gitignore；可清理但不影响功能

---

## 十三、事故记录（教训）

### 源码毁灭事故

上一轮重构时，脚本里写了：

```powershell
if (Test-Path $newDir) { Remove-Item $newDir -Recurse -Force }
Move-Item $oldDir $newDir
```

因为目标目录已存在，`Remove-Item -Recurse -Force` **删掉了整个 `cn` 树，包含全部 22 个 .kt 源文件**，随后 `Move-Item` 失败。

**标准：在执行任何递归删除前，必须打印解析后的绝对路径和目录内容，确认是目标对象再删。优先移动到归档而不是删除。**

### 其他值得记的

- 用 PowerShell 的 `System.Drawing` 画图会遇到数组运算语义、`New-Object` 构造器歧义、UTF-8 BOM 三个坑。几何绘制改用 Python 纯计算
- 批量正则替换 Kotlin 源码时，跨行模板字符串与注释会被误伤。要么收紧正则（只匹配单行、不含 `$` 插值），要么逐条 `edit`。**改完必须编译验证**
- adb 传输二进制会损坏，必须 base64 编码后 push，再 `base64 -d`
- `adb shell screencap` 重定向到本地文件会损坏 PNG，必须先存到设备再 `adb pull`
- 坐标点击不可靠（布局会变），用 `scripts/uihelp.py` 按文字定位

---

## 十四、给下一个会话的第一件事

```powershell
cd "D:\Documents\deepseek-harness\default-workspace\inventory-android"
git log --oneline -3          # 确认在 04c1cdf 附近
git status                    # 确认工作区干净
Get-ChildItem app\src\main\java\cn\levanll\inventory -Recurse -Filter *.kt | Measure-Object
# 应该是 25 个文件
```

然后读 `docs/设计方案.md`，再动手。

**用户偏好**：中文交流、直奔结论、不写客套话、给证据不给承诺、遇到阻塞说清「卡在哪 + 排除了什么 + 需要什么决策」。
