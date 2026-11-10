"""创建 GitHub Release 并上传 APK。

版本号不写死：从命令行传入，APK 名与 tag 自动推导。

用法：
    python scripts/create_release.py 1.3.0 --body notes/v1.3.0.md
    python scripts/create_release.py 1.3.0 --body-file notes.md --dry-run

Release 描述必须中英对照：中文段 ## 本次更新，英文段 ## What's new，
--- 分隔，章节对称。脚本会校验这两段都在。

用 Python 的 json.dumps(ensure_ascii=False).encode('utf-8') 发请求 ——
PowerShell 直接转 JSON 会把中文变成问号。

token 从 lanpulse/.gh_token 读取，只读不改该目录，只在请求头里临时用。
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.request

TOKEN_PATH = r"D:\Documents\deepseek-harness\default-workspace\lanpulse\.gh_token"
REPO = "Levanll/InventoryLedger"
PROJECT = r"D:\Documents\deepseek-harness\default-workspace\inventory-android"
APK = os.path.join(PROJECT, "app", "build", "outputs", "apk", "release", "app-release.apk")


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
        # ensure_ascii=False + utf-8 编码是中文不乱码的关键
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


def validate_body(body):
    """中英对照结构校验。缺任一段就中止，不要发出半成品。"""
    problems = []
    if "## 本次更新" not in body:
        problems.append("缺少中文段标题 ## 本次更新")
    if "## What's new" not in body:
        problems.append("缺少英文段标题 ## What's new")
    if "\n---\n" not in body:
        problems.append("缺少 --- 分隔线")
    if "???" in body:
        problems.append("含 ??? —— 中文编码出错")
    return problems


def main():
    ap = argparse.ArgumentParser(description="创建 GitHub Release 并上传 APK")
    ap.add_argument("version", help="版本号，如 1.3.0（不带 v 前缀）")
    ap.add_argument("--body", required=True, help="Release 描述文件路径（Markdown）")
    ap.add_argument("--apk", default=APK, help="APK 路径，默认取 release 构建产物")
    ap.add_argument("--dry-run", action="store_true", help="只校验不发送")
    args = ap.parse_args()

    version = args.version.lstrip("v")
    tag = f"v{version}"
    apk_name = f"InventoryLedger-{version}.apk"

    if not os.path.exists(args.body):
        sys.exit(f"描述文件不存在: {args.body}")
    with open(args.body, encoding="utf-8") as f:
        body = f.read()

    problems = validate_body(body)
    if problems:
        sys.exit("描述结构不合格:\n  " + "\n  ".join(problems))

    if not os.path.exists(args.apk):
        sys.exit(f"APK 不存在: {args.apk}")

    print(f"tag:  {tag}")
    print(f"APK:  {args.apk}  ({os.path.getsize(args.apk):,} bytes) → {apk_name}")
    print(f"body: {len(body)} 字符，中英段落齐全")

    if args.dry_run:
        print("\n--dry-run：未发送任何请求")
        return

    # 已存在同名 Release 时先删（便于重跑）
    status, existing = api(f"/releases/tags/{tag}")
    if status == 200:
        rid = existing["id"]
        print(f"\n已存在 Release {tag}（id={rid}），先删除")
        api(f"/releases/{rid}", method="DELETE")

    print("\n创建 Release…")
    status, rel = api(
        "/releases",
        method="POST",
        payload={
            "tag_name": tag,
            "name": tag,
            "body": body,
            "draft": False,
            "prerelease": False,
        },
    )
    if status not in (200, 201):
        sys.exit(f"创建失败 status={status}\n{rel}")

    print(f"  已创建 id={rel['id']}")

    # 服务端回读校验，确认中文没在传输中损坏
    problems = validate_body(rel["body"])
    if problems:
        sys.exit("服务端返回的 body 不合格:\n  " + "\n  ".join(problems))
    print("  ✓ 服务端 body 校验通过")

    print("上传 APK…")
    status, asset = upload_asset(rel["upload_url"], args.apk, apk_name)
    if status not in (200, 201):
        sys.exit(f"上传失败 status={status}\n{asset}")

    print(f"  ✓ {asset['name']}  {asset['size']:,} bytes")
    print(f"\nRelease URL: {rel['html_url']}")


if __name__ == "__main__":
    main()
