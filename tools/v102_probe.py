#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""v1.0.2 验证：目录隔离 / 目录计数与扫描时间 / 数据新鲜度提示 / 返回顶部"""
import subprocess, time, re, os, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\v102.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

results = []


def sh(*a, timeout=180):
    try:
        return subprocess.run([ADB, "-s", DEV] + list(a), capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/v102.xml")
    sh("pull", "/sdcard/v102.xml", DUMP)
    try:
        root = ET.parse(DUMP).getroot()
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        t = n.get("text", "") or ("@" + n.get("content-desc", "") if n.get("content-desc") else "")
        m = BOUNDS.match(n.get("bounds", "") or "")
        if m:
            l, tp, r, bo = (int(v) for v in m.groups())
            out.append((t or "", (l + r) // 2, (tp + bo) // 2))
    return out


def T(items):
    return [t for t, x, y in items]


def find(items, kw, ymax=10 ** 9):
    for t, x, y in items:
        if kw in t and y <= ymax:
            return x, y
    return None


def find_exact(items, text, ymax=10 ** 9):
    for t, x, y in items:
        if t == text and y <= ymax:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def shot(name):
    data = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                          capture_output=True, timeout=180).stdout
    with open(os.path.join(SHOTS, name), "wb") as f:
        f.write(data)


def ok(cond, msg):
    results.append(cond)
    print(("  OK   " if cond else "  FAIL ") + msg)


def header(items):
    return next((t for t in T(items) if "|" in t and "全库" in t), "")


def folder_chip(items, name):
    """目录芯片位于顶部区域(y<320)，且文本恰好是目录名"""
    return find_exact(items, name, ymax=320)


def chip_info(items, name):
    """取目录芯片的第二行（数量 · 时间）。
    阈值必须收紧：芯片横向相邻，阈值过宽会读到隔壁芯片的第二行。"""
    pos = folder_chip(items, name)
    if not pos:
        return ""
    for t, x, y in items:
        if abs(x - pos[0]) < 70 and 0 < (y - pos[1]) < 40 and ("个 ·" in t):
            return t
    return ""


def main():
    print("=== 步骤 1: 冷启动，检查顶栏与目录入口 ===")
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(12)
    items = dump()
    print("   顶栏:", header(items))

    ok(find(items, "@返回顶部") is not None, "存在「返回顶部」按钮")
    bt = find(items, "@返回顶部")
    sh_icon = find(items, "@查看白名单")
    if bt and sh_icon:
        ok(bt[0] < sh_icon[0], "返回顶部在盾牌标左侧 (x %d < %d)" % (bt[0], sh_icon[0]))

    for name in ["DCIM", "Pictures", "Movies", "Download", "其他"]:
        pos = folder_chip(items, name)
        info = chip_info(items, name)
        print("   芯片 %-9s -> %s" % (name, info or "(未找到)"))
        ok(pos is not None, "目录入口「%s」存在" % name)
    shot("18_folder_entries.png")

    print("=== 步骤 2: 切到 Movies，应只显示 Movies 目录的视频 ===")
    p = folder_chip(items, "Movies")
    if p:
        tap(p[0], p[1])
        time.sleep(5)
    items = dump()
    h = header(items)
    print("   顶栏:", h)
    ok("Movies 3" in h or "Movies 3" in h, "主列表切换为 Movies (3 条) -> %s" % h)
    # 断言列表里出现的路径全部属于 Movies
    paths = [t for t in T(items) if t.startswith("/storage")]
    bad = [p for p in paths if "/Movies/" not in p]
    ok(not bad, "列表内不存在其他目录的路径 (越界 %d 条)" % len(bad))
    print("   可见路径:", paths[:3])

    print("=== 步骤 3: 切到 DCIM，应为空且提示未扫描 ===")
    p = folder_chip(items, "DCIM")
    if p:
        tap(p[0], p[1])
        time.sleep(5)
    items = dump()
    h = header(items)
    print("   顶栏:", h)
    ok("DCIM 0" in h, "DCIM 目录为空 -> %s" % h)
    hints = [t for t in T(items) if "尚未扫描" in t or "可能不是最新" in t]
    print("   提示:", hints[:2])
    ok(len(hints) > 0, "显示数据新鲜度提示")

    print("=== 步骤 4: 扫描 DCIM ===")
    p = find(items, "扫描 DCIM")
    if p:
        tap(*p)
        time.sleep(22)
    items = dump()
    ok(any("DCIM扫描完成" in t for t in T(items)), "DCIM 扫描完成提示 -> %s"
       % [t for t in T(items) if "扫描完成" in t][:1])
    ok(any(chip_info(items, "DCIM") and "未扫描" not in chip_info(items, "DCIM")
           for _ in [0]), "DCIM 芯片已记录扫描时间 -> %s" % chip_info(items, "DCIM"))

    print("=== 步骤 5: 再扫一次 Movies，令两个目录都有扫描记录 ===")
    p = folder_chip(items, "Movies")
    if p:
        tap(p[0], p[1])
        time.sleep(5)
    items = dump()
    p = find(items, "扫描 Movies")
    if p:
        tap(*p)
        time.sleep(22)
    items = dump()
    print("   Movies 芯片:", chip_info(items, "Movies"))
    ok("未扫描" not in chip_info(items, "Movies"), "Movies 已记录扫描时间")

    print("=== 步骤 6: 切回 DCIM，应提示「可能不是最新」 ===")
    p = folder_chip(items, "DCIM")
    if p:
        tap(p[0], p[1])
        time.sleep(5)
    items = dump()
    hints = [t for t in T(items) if "可能不是最新" in t or "尚未扫描" in t]
    print("   提示:", hints[:1])
    ok(any("可能不是最新" in t for t in hints),
       "切到较早扫描的目录时提示可能不是最新")
    ok("DCIM 0" in header(items), "DCIM 仍为空 -> %s" % header(items))
    shot("19_stale_hint.png")

    print("=== 步骤 7: 返回顶部按钮可点击 ===")
    sh("shell", "input", "swipe", "270", "1000", "270", "300", "400")
    time.sleep(2)
    sh("shell", "input", "swipe", "270", "1000", "270", "300", "400")
    time.sleep(3)
    items = dump()
    p = find(items, "@返回顶部")
    if p:
        tap(*p)
        time.sleep(3)
    items = dump()
    ok(any("视频高级过滤器" in t for t in T(items)), "点击后回到列表顶部（可见筛选面板标题）")


if __name__ == "__main__":
    main()
    print("\n================ 汇总 ================")
    print("通过 %d / %d" % (sum(1 for r in results if r), len(results)))
