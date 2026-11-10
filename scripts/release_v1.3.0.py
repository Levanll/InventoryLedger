"""创建 GitHub Release v1.3.0 并上传 APK。

Release 描述必须中英对照：中文段 ## 本次更新，英文段 ## What's new，--- 分隔，章节对称。

用 Python 的 json.dumps(ensure_ascii=False).encode('utf-8') 发请求 ——
PowerShell 直接转 JSON 会把中文变成问号。

token 从 lanpulse/.gh_token 读取，只读不改该目录。
"""

import json
import os
import subprocess
import sys
import urllib.error
import urllib.request

TOKEN_PATH = r"D:\Documents\deepseek-harness\default-workspace\lanpulse\.gh_token"
REPO = "Levanll/InventoryLedger"
TAG = "v1.3.0"
APK = r"D:\Documents\deepseek-harness\default-workspace\inventory-android\app\build\outputs\apk\release\app-release.apk"
APK_NAME = "InventoryLedger-1.3.0.apk"

BODY = """## 本次更新

归零商品有了真正的去处，中英双语补齐到错误提示与流水备注。

### 新增

- **「已用完」分区**：数量归零且选择「保留记录」的商品，收进列表底部一个默认折叠的分区，展开后可「恢复」或「删除」。此前它们会直接从列表消失，用户点了「保留」却看不到任何东西
- **从已用完恢复**：点「恢复」把商品加回 1 件并写一条入库流水，历史归属不变

### 改进

- **错误提示双语**：约 30 处业务校验此前在英文界面下仍显示中文，现按当前语言返回
- **流水备注双语**：明细页的备注（快速加减、撤销等）不再中英混杂
- **写操作全部收进事务**：条码改名+记忆、表单记忆、条码命中计数、种子写入
- **列表底部留白**：最后一行按钮不再被悬浮按钮遮挡

### 修复

- 「保留记录」后列表不刷新，看不到任何变化
- R8 规则包名写错（`com.example` vs `cn.levanll`），四条 keep 规则从未生效
- 分类路径二级分类未翻译
- 扫码页英文文案残缺（「未获得相机权限，无法Scan」）
- 删除确认弹窗、条码冲突弹窗正文未翻译
- 英文分类名过长时分类路径被挤成竖排

### 安全

- **更换 keystore 密码**：此前明文写在文档里并推送到公开仓库。已更换，证书指纹未变，已安装用户仍可覆盖安装

### 下载

| 文件 | 大小 | 适用 |
|---|---|---|
| `InventoryLedger-1.3.0.apk` | 2.61 MB | Android 7.0 (API 24) 及以上 |

**安装**：下载后允许「未知来源」安装。签名与旧版相同，可直接覆盖升级，数据保留。

---

<!-- English version below / 以下为英文版 -->

## What's new

Zeroed items finally have a home, and the bilingual UI now covers error messages and record notes.

### Added

- **Used up section**: items reduced to zero and kept with "Keep record" collect in a collapsed section at the bottom of the list, where they can be restored or deleted. Previously they vanished from the list immediately, so tapping "Keep" appeared to do nothing
- **Restore from used up**: restores the item to 1 and writes an inbound record; history stays intact

### Improvements

- **Bilingual error messages**: about 30 validation errors showed Chinese in the English UI; they now follow the current language
- **Bilingual record notes**: notes in the detail view (quick add/remove, revert) no longer mix languages
- **All writes inside transactions**: barcode rename + memo, form memo, barcode hit counter, seed writes
- **List bottom padding**: the last row's buttons are no longer covered by the floating button

### Fixes

- The list did not refresh after "Keep record", so nothing appeared to change
- R8 rules used the wrong package name (`com.example` vs `cn.levanll`), so all four keep rules had never applied
- Level-2 category names were not translated in the item path
- The English camera-permission message was garbled ("未获得相机权限，无法Scan")
- The delete-confirmation and barcode-conflict dialogs had untranslated body text
- Long English category names squeezed the category path into vertical single characters

### Security

- **Rotated the keystore password**: it had been committed in plain text and pushed to the public repo. Rotated; the certificate fingerprint is unchanged, so existing installs still update in place

### Downloads

| File | Size | Requirements |
|---|---|---|
| `InventoryLedger-1.3.0.apk` | 2.61 MB | Android 7.0 (API 24) and above |

**Install**: download and allow "unknown sources". Signed with the same key, so it installs over the previous version with data intact.
"""


def token():
    with open(TOKEN_PATH, encoding="utf-8") as f:
        return f.read().strip()


def api(path, method="GET", payload=None):
    url = f"https://api.github.com/repos/{REPO}{path}"
    data = None
    headers = {
        "Authorization": f"Bearer {token()}",
        "Accept": "application/vnd.github+json",
        "User-Agent": "InventoryLedger-release",
    }
    if payload is not None:
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json; charset=utf-8"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8", errors="replace")
        return e.code, body


def upload_asset(upload_url, path, name):
    with open(path, "rb") as f:
        content = f.read()
    url = upload_url.replace("{?name,label}", f"?name={name}")
    req = urllib.request.Request(
        url,
        data=content,
        headers={
            "Authorization": f"Bearer {token()}",
            "Content-Type": "application/vnd.android.package-archive",
            "User-Agent": "InventoryLedger-release",
        },
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=300) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", errors="replace")


def main():
    if not os.path.exists(APK):
        sys.exit(f"APK 不存在: {APK}")

    print(f"APK: {os.path.getsize(APK):,} bytes")

    # 已存在同名 Release 时先删（便于重跑）
    status, existing = api(f"/releases/tags/{TAG}")
    if status == 200:
        rid = existing["id"]
        print(f"已存在 Release {TAG}（id={rid}），先删除")
        api(f"/releases/{rid}", method="DELETE")

    print("创建 Release…")
    status, rel = api(
        "/releases",
        method="POST",
        payload={
            "tag_name": TAG,
            "name": TAG,
            "body": BODY,
            "draft": False,
            "prerelease": False,
        },
    )
    if status not in (200, 201):
        sys.exit(f"创建失败 status={status}\n{rel}")

    print(f"  已创建 id={rel['id']}")
    print(f"  body 长度 {len(rel['body'])}")

    # 校验中文没变问号
    if "???" in rel["body"]:
        sys.exit("✗ body 含 ??? —— 中文编码出错")
    if "## 本次更新" not in rel["body"] or "## What's new" not in rel["body"]:
        sys.exit("✗ 缺少中英段落标题")
    print("  ✓ 中英段落完整，无乱码")

    print("上传 APK…")
    status, asset = upload_asset(rel["upload_url"], APK, APK_NAME)
    if status not in (200, 201):
        sys.exit(f"上传失败 status={status}\n{asset}")

    print(f"  ✓ {asset['name']}  {asset['size']:,} bytes")
    print(f"\nRelease URL: {rel['html_url']}")


if __name__ == "__main__":
    main()
