#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""v1.0.1 新功能验证：范围扫描 / 白名单 / 相对路径输入"""
import subprocess, time, re, os, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\v101.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

results = []


def sh(*a, timeout=150):
    try:
        return subprocess.run([ADB, "-s", DEV] + list(a), capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/v101.xml")
    sh("pull", "/sdcard/v101.xml", DUMP)
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


def find(items, kw):
    for t, x, y in items:
        if kw in t:
            return x, y
    return None


def find_exact(items, text):
    for t, x, y in items:
        if t == text:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def longpress(x, y):
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1000")


def shot(name):
    data = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                          capture_output=True, timeout=150).stdout
    with open(os.path.join(SHOTS, name), "wb") as f:
        f.write(data)


def stat(items):
    for t in T(items):
        if t.startswith("总收录"):
            return t
    return "?"


def ok(cond, msg):
    results.append(cond)
    print(("  OK   " if cond else "  FAIL ") + msg)


def collapse_panel():
    """幂等：仅在面板展开时才点折叠"""
    items = dump()
    if not any("封装格式" in t for t in T(items)):
        return
    p = find(items, "@展开收起")
    if p:
        tap(*p)
        time.sleep(3)


def scan(items, scope_label):
    p = find_exact(items, scope_label)
    if p:
        tap(*p)
        time.sleep(2)
        items = dump()
    p = find(items, "扫描 ")
    if p:
        tap(*p)
        time.sleep(20)
    return dump()


def main():
    print("=== 步骤 1: 范围扫描隔离性（DCIM 无视频，不得误删 Movies 记录）===")
    items = dump()
    before = stat(items)
    print("   扫描前:", before)
    items = scan(items, "DCIM")
    after_dcim = stat(items)
    print("   扫描 DCIM 后:", after_dcim)
    ok(after_dcim == before, "限定范围扫描未误删范围外记录 -> %s" % after_dcim)

    print("=== 步骤 2: 切到 Movies 范围扫描 ===")
    items = scan(items, "Movies")
    after_movies = stat(items)
    print("   扫描 Movies 后:", after_movies)
    ok("总收录: 3" in after_movies, "Movies 范围命中 3 个视频 -> %s" % after_movies)

    print("=== 步骤 3: 长按卡片加入白名单 ===")
    collapse_panel()
    items = dump()
    card = None
    for t, x, y in items:
        if t.startswith("@") and t.endswith(".mp4"):
            card = (x, y)
            break
    if not card:
        print("   !! 未找到卡片")
        return
    longpress(*card)
    time.sleep(4)
    items = dump()
    ok(any(t.startswith("已选") for t in T(items)), "长按进入多选 -> %s"
       % [t for t in T(items) if t.startswith("已选")])
    ok(any("白名单 (" in t for t in T(items)), "底栏出现白名单按钮 -> %s"
       % [t for t in T(items) if "白名单 (" in t])

    p = find(items, "白名单 (")
    if p:
        tap(*p)
        time.sleep(6)
    items = dump()
    st = stat(items)
    print("   加入白名单后:", st)
    ok("白名单: 1" in st, "白名单计数 +1 -> %s" % st)
    ok("总收录: 3" in st, "总收录不变（白名单只是从审核列表移出）-> %s" % st)
    shot("14_whitelist_added.png")

    print("=== 步骤 4: 打开白名单面板查看 ===")
    p = find(items, "@查看白名单")
    if p:
        tap(*p)
        time.sleep(4)
    items = dump()
    wl = [t for t in T(items) if "白名单" in t]
    print("   面板文本:", wl[:5])
    ok(any("白名单 (1)" in t for t in T(items)), "白名单面板显示 1 条 -> %s" % wl[:3])
    shot("15_whitelist_panel.png")

    print("=== 步骤 5: 移出白名单 ===")
    p = find_exact(items, "移出白名单")
    if p:
        tap(*p)
        time.sleep(5)
    items = dump()
    ok(any("白名单还是空的" in t or "白名单 (0)" in t for t in T(items)),
       "白名单已清空 -> %s" % [t for t in T(items) if "白名单" in t][:3])

    p = find_exact(items, "@关闭白名单")
    if p:
        tap(*p)
        time.sleep(4)
    items = dump()
    st = stat(items)
    print("   移出后:", st)
    ok("白名单: 0" in st and "总收录: 3" in st, "移出后主列表恢复 -> %s" % st)

    print("=== 步骤 6: 输出目录相对路径输入 ===")
    collapse_panel()
    items = dump()
    card = None
    for t, x, y in items:
        if t.startswith("@") and t.endswith(".mp4"):
            card = (x, y)
            break
    if card:
        longpress(*card)
        time.sleep(4)
        items = dump()
    p = find(items, "@修改目标目录")
    if p:
        tap(*p)
        time.sleep(4)
    items = dump()
    ok(any("输出目录" in t for t in T(items)), "输出目录弹窗打开")
    ok(any("相对路径" in t for t in T(items)), "存在相对路径输入框")
    ok(any("用文件管理器选择" in t for t in T(items)), "存在文件管理器选择按钮")
    print("   实际位置提示:", [t for t in T(items) if "实际位置" in t])
    shot("16_folder_relative_input.png")


if __name__ == "__main__":
    main()
    print("\n================ 汇总 ================")
    print("通过 %d / %d" % (sum(1 for r in results if r), len(results)))
