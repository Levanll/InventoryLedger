"""为站点截图灌入演示数据。

设备上没有 sqlite3，所以走「拉库 → 本地改 → 推回」：
    run-as base64 拉取 → 本地 sqlite3 改 → base64 推回

前置：装 debug 包并启动过一次，让数据库与 80 个分类种子生成好。

用法：
    python scripts/seed_demo.py
"""

import base64
import os
import shutil
import sqlite3
import subprocess
import sys
import time

ADB = r"F:\android-dev\sdk\platform-tools\adb.exe"
PKG = "cn.levanll.inventory"
DB_DIR = f"/data/data/{PKG}/databases"
DB = f"{DB_DIR}/inventory.db"


def detect_serial():
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
        sys.exit(f"连接了多台设备 {serials}，请设置 ADB_SERIAL 指定")
    return serials[0]


SERIAL = detect_serial()


def adb(*args, check=False):
    return subprocess.run([ADB, "-s", SERIAL] + list(args),
                          capture_output=True, text=True, check=check)


def run_as(*args):
    return adb("shell", "run-as", PKG, *args)


def pull_db(work_dir):
    """用 base64 拉取（adb 直传二进制会损坏）。"""
    os.makedirs(work_dir, exist_ok=True)
    for name in ["inventory.db", "inventory.db-wal"]:
        r = run_as("base64", f"{DB_DIR}/{name}")
        if r.returncode != 0 or not r.stdout.strip():
            print(f"  跳过 {name}（读取失败或不存在）")
            continue
        data = base64.b64decode("".join(r.stdout.split()))
        with open(os.path.join(work_dir, name), "wb") as f:
            f.write(data)
        print(f"  拉取 {name}  {len(data):,} bytes")


def push_db(work_dir):
    """推回数据库。

    不能用 adb push 直接写应用私有目录（权限不足），也不能用 echo 拼接
    （命令行会超长）。

    关键点：`run-as pkg sh -c 'cmd > path'` 里的重定向由 sh 执行，
    而 sh 以 shell 身份运行，写不了私有目录。必须先 `cat` 到 run-as
    自己的 home（可写），再 `cp` 到 databases —— 这两步都在 run-as 进程内。
    """
    src = os.path.join(work_dir, "inventory.db")
    tmp = "/data/local/tmp/_seed.db"

    r = adb("push", src, tmp)
    if r.returncode != 0:
        sys.exit(f"push 失败: {r.stderr.strip()}")

    # 用 dd 而不是 shell 重定向：`run-as pkg sh -c 'cat x > y'` 的重定向
    # 会被 SELinux 拒绝（runas_app 域），dd 直接 open 目标文件则可行。
    r = run_as("dd", f"if={tmp}", f"of={DB}")
    if r.returncode != 0 or "error" in r.stderr.lower():
        sys.exit(f"写入失败: {r.stderr.strip()[:300]}")
    print("  写入数据库: OK")

    # 清掉旧 WAL/SHM，避免与新库不一致
    run_as("rm", "-f", f"{DB_DIR}/inventory.db-wal", f"{DB_DIR}/inventory.db-shm")
    adb("shell", f"rm -f {tmp}")


# (名称, 规格, 分类路径, 数量, 剩余天数或 None, 单位, 低库存阈值)
# 剩余天数：负数 = 已过期，0-7 = 紧急，8-30 = 临近，>30 = 正常，None = 不启用保质期
ITEMS = [
    # 服装 / 上装 / T恤 —— 组内临期优先、已过期排最前
    ("森马纯棉T恤", "黑色 L", "服装/上装/T恤", 3, -3, "件", 0),
    ("太平鸟印花T恤", "白色 XL", "服装/上装/T恤", 2, 12, "件", 0),
    ("优衣库 U领白T", "白色 M", "服装/上装/T恤", 5, None, "件", 0),
    ("李宁运动T恤", "灰色 L", "服装/上装/T恤", 1, None, "件", 0),
    ("无印良品素T", "藏青 M", "服装/上装/T恤", 4, None, "件", 0),

    # 服装 / 上装 / 衬衫
    ("优衣库牛津纺衬衫", "蓝 M", "服装/上装/衬衫", 2, None, "件", 0),
    ("H&M 格纹衬衫", "红 L", "服装/上装/衬衫", 1, None, "件", 0),

    # 食品 / 生鲜 / 乳制品 —— 临期徽标
    ("伊利纯牛奶", "250ml", "食品/生鲜/乳制品", 6, 2, "盒", 2),
    ("蒙牛酸奶", "100g×8", "食品/生鲜/乳制品", 2, 9, "盒", 0),
    ("君乐宝鲜奶", "450ml", "食品/生鲜/乳制品", 1, 21, "瓶", 0),
    ("光明奶酪棒", "20g×5", "食品/生鲜/乳制品", 3, None, "包", 0),

    # 食品 / 零食 / 坚果糖果
    ("三只松鼠每日坚果", "25g×30", "食品/零食/坚果糖果", 2, 180, "盒", 0),
    ("士力架", "51g", "食品/零食/坚果糖果", 5, None, "件", 0),

    # 美妆个护 / 护肤 / 精华 —— 已过期最久的排最前
    ("欧莱雅复颜精华", "30ml", "美妆个护/护肤/精华", 1, -15, "瓶", 0),
    ("兰蔻小黑瓶精华", "30ml", "美妆个护/护肤/精华", 1, 150, "瓶", 0),
    ("雅诗兰黛小棕瓶", "50ml", "美妆个护/护肤/精华", 1, 400, "瓶", 0),

    # 美妆个护 / 护肤 / 面膜
    ("敷尔佳白膜", "5片", "美妆个护/护肤/面膜", 4, 6, "盒", 0),
    ("春雨蜂蜜面膜", "10片", "美妆个护/护肤/面膜", 2, 300, "盒", 0),

    # 美妆个护 / 彩妆 / 口红
    ("MAC 子弹头口红", "Chili", "美妆个护/彩妆/口红", 2, None, "件", 0),
    ("YSL 小金条", "#21", "美妆个护/彩妆/口红", 1, None, "件", 0),

    # 数码配件 / 线材 / 数据线
    ("Anker USB-C 数据线", "1m", "数码配件/线材/数据线", 3, None, "件", 0),

    # ---- 已用完（数量 0，未删除）—— 展示「已用完」分区 ----
    ("德芙巧克力", "43g", "食品/零食/坚果糖果", 0, None, "件", 0),
    ("清风抽纸", "3层×130抽", "日用家居/纸品/抽纸", 0, None, "包", 0),
]


def find_cat_id(cur, path):
    parts = path.split("/")
    pid = None
    for name in parts:
        row = cur.execute(
            "SELECT id FROM category WHERE name=? AND parent_id IS ?", (name, pid)
        ).fetchone()
        if not row:
            return None
        pid = row[0]
    return pid


def seed(db_path):
    con = sqlite3.connect(db_path)
    cur = con.cursor()
    now = int(time.time() * 1000)

    cur.execute("DELETE FROM stock_record")
    cur.execute("DELETE FROM item")
    cur.execute("DELETE FROM barcode_memo")
    cur.execute("DELETE FROM sqlite_sequence WHERE name IN ('item','stock_record','barcode_memo')")

    missing = set()
    for name, spec, cat, qty, days, unit, low in ITEMS:
        cid = find_cat_id(cur, cat)
        if cid is None:
            missing.add(cat)
            continue

        if days is None:
            exp_enabled, prod, shelf = 0, None, None
        else:
            exp_enabled = 1
            if days >= 0:
                shelf = 60
                prod = now - (60 - days) * 86400000
            else:
                shelf = 30
                prod = now - (30 + abs(days)) * 86400000

        cur.execute(
            "INSERT INTO item(name,spec,unit,quantity,low_threshold,category_id,"
            "expiry_enabled,production_date,shelf_life_days,created_at,updated_at) "
            "VALUES(?,?,?,?,?,?,?,?,?,?,?)",
            (name, spec, unit, qty, low, cid, exp_enabled, prod, shelf, now, now),
        )

    # 每个在库商品补一条入库流水，让统计页有数据
    cur.execute(
        "INSERT INTO stock_record(item_id,type,delta,before_qty,after_qty,reason,note,created_at) "
        "SELECT id,'IN',quantity,0,quantity,'MANUAL','Initial entry',? FROM item WHERE quantity > 0",
        (now,),
    )
    # 已用完的商品留一条「用完」流水，历史可追溯
    cur.execute(
        "INSERT INTO stock_record(item_id,type,delta,before_qty,after_qty,reason,note,created_at) "
        "SELECT id,'OUT',-1,1,0,'MANUAL','Quick remove',? FROM item WHERE quantity = 0",
        (now,),
    )

    con.commit()

    n_items = cur.execute("SELECT COUNT(*) FROM item").fetchone()[0]
    n_records = cur.execute("SELECT COUNT(*) FROM stock_record").fetchone()[0]
    n_zero = cur.execute("SELECT COUNT(*) FROM item WHERE quantity = 0").fetchone()[0]
    con.close()

    print(f"  商品 {n_items} 个（其中已用完 {n_zero} 个）")
    print(f"  流水 {n_records} 条")
    if missing:
        print(f"  ⚠ 未找到分类: {sorted(missing)}")
    return not missing


def main():
    work = os.path.join(os.environ["TEMP"], "seed_work")
    if os.path.isdir(work):
        shutil.rmtree(work)

    print("1) 停止应用")
    adb("shell", "am", "force-stop", PKG)

    print("2) 拉取数据库")
    pull_db(work)

    local_db = os.path.join(work, "inventory.db")
    if not os.path.exists(local_db):
        sys.exit("拉取失败，数据库不存在")

    print("3) 灌入演示数据")
    ok = seed(local_db)

    print("4) 推回设备")
    push_db(work)

    print("5) 重启应用")
    adb("shell", "am", "start", "-n", f"{PKG}/.MainActivity")

    print("\n完成" + ("" if ok else "（有分类缺失，请检查分类树）"))


if __name__ == "__main__":
    main()
