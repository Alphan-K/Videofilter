# -*- coding: utf-8 -*-
"""v1.0.4 补充验证：开关的「关 -> 不弹窗 / 开 -> 弹窗」双向行为。

上一轮的步骤 11 是空转的：重启后应用自动把浏览目录切到了 Movies（目录自愈逻辑），
脚本没先切回 DCIM 就去找「扫描 DCIM」按钮，自然找不到，于是「没弹窗」在没扫描的情况下
误判为通过。本脚本先显式切目录，再扫描，并且要求扫出候选，否则断言无意义。
"""
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.videofilter.app"
DUMP_LOCAL = r"C:\Users\tony\.workbuddy\binaries\_dl\v104c.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

RESULTS = []


def sh(*args, timeout=300):
    return subprocess.run([ADB, "-s", DEV] + list(args), capture_output=True,
                          text=True, encoding="utf-8", errors="replace", timeout=timeout).stdout


def dump():
    for _ in range(6):
        if "dumped to" not in sh("shell", "uiautomator", "dump", "/sdcard/v104c.xml"):
            time.sleep(2)
            continue
        sh("pull", "/sdcard/v104c.xml", DUMP_LOCAL)
        try:
            root = ET.parse(DUMP_LOCAL).getroot()
        except Exception:
            time.sleep(2)
            continue
        rows = []
        for n in root.iter("node"):
            text = n.get("text", "") or ("@" + n.get("content-desc", "") if n.get("content-desc") else "")
            m = BOUNDS.match(n.get("bounds", "") or "")
            if not m:
                continue
            l, t, r, b = (int(v) for v in m.groups())
            rows.append({"text": text, "cls": n.get("class", ""), "cx": (l + r) // 2,
                         "cy": (t + b) // 2, "top": t, "bottom": b,
                         "checkable": n.get("checkable") == "true",
                         "checked": n.get("checked") == "true"})
        if rows:
            return rows
        time.sleep(2)
    return []


def T(items):
    return [i["text"] for i in items]


def find_exact(items, label, ymin=0, ymax=10 ** 9):
    for i in items:
        if i["text"] == label and ymin <= i["cy"] <= ymax:
            return i
    return None


def find_contains(items, kw, ymin=0, ymax=10 ** 9):
    for i in items:
        if kw in i["text"] and ymin <= i["cy"] <= ymax:
            return i
    return None


def header(items):
    for i in items:
        if "全库" in i["text"]:
            return i["text"]
    return ""


def tap(item, wait=3):
    sh("shell", "input", "tap", str(item["cx"]), str(item["cy"]))
    time.sleep(wait)


def scroll_top():
    for _ in range(3):
        sh("shell", "input", "swipe", "270", "700", "270", "1100", "300")
        time.sleep(1)


def shot(name):
    data = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                          capture_output=True, timeout=240).stdout
    with open(os.path.join(SHOTS, name), "wb") as f:
        f.write(data)
    print("   截图 -> %s (%d bytes)" % (name, len(data)))


def ok(cond, msg):
    RESULTS.append((bool(cond), msg))
    print("   %s %s" % ("[PASS]" if cond else "[FAIL]", msg))


def wait_for(pred, timeout=220, interval=3):
    t0 = time.time()
    items = []
    while time.time() - t0 < timeout:
        items = dump()
        if items and pred(items):
            return items
        time.sleep(interval)
    return items


def ls(path):
    return [x for x in sh("shell", "ls", path).replace("\r", "").strip().split("\n") if x]


def launch():
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    return wait_for(lambda i: header(i) != "", timeout=120)


def goto_dcim(items):
    """显式把浏览目录切到 DCIM，并确认切换生效"""
    p = find_exact(items, "DCIM", ymax=330)
    if not p:
        return items, False
    tap(p, wait=4)
    items = dump()
    return items, header(items).startswith("DCIM")


def scan_and_wait(items, label="扫描 DCIM"):
    p = find_exact(items, label) or find_contains(items, label)
    if not p:
        print("   !! 找不到 %s，当前文本: %s" % (label, T(items)[:14]))
        return False
    tap(p, wait=1)
    return True


# ============================================================
print("=== 环境确认 ===")
sh("shell", "appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
sh("shell", "settings", "put", "global", "hide_error_dialogs", "1")
print("   DCIM:", " | ".join(ls("/sdcard/DCIM/Camera")))
print("   目标:", " | ".join(ls("/sdcard/Movies/FilteredVideos")))

print("=== A) 开关为「关」时：有候选也不弹窗 ===")
items = launch()
scroll_top()
items = dump()
items, on_dcim = goto_dcim(items)
print("   顶栏:", header(items))
ok(on_dcim, "已显式切到 DCIM 目录 -> %s" % header(items))

if not scan_and_wait(items):
    sys.exit(1)
items = wait_for(lambda i: any("扫描完成" in t for t in T(i)), timeout=220)
time.sleep(5)
items = dump()
hdr = header(items)
has_prompt = any("是否移动到" in t for t in T(items))
print("   顶栏:", hdr)
print("   结果条:", [t for t in T(items) if "扫描完成" in t][:1])
ok("1/1" in hdr, "DCIM 确有 1 个候选通过筛选（非空转）-> %s" % hdr)
ok(any("扫描完成" in t for t in T(items)), "扫描确实执行完成")
ok(not has_prompt, "开关关闭 -> 有候选也不弹窗")
shot("27_no_prompt_after_off.png")

print("=== B) 重新打开 DCIM 开关 ===")
scroll_top()
items = dump()
p = find_exact(items, "@自动询问设置")
if not p:
    print("   !! 找不到设置入口")
    sys.exit(1)
tap(p, wait=4)
items = dump()
a = find_exact(items, "扫描后自动询问移动")
ay = a["cy"] if a else 0
lbl = find_exact(items, "DCIM", ymin=ay)
if not lbl:
    print("   !! 设置面板里找不到 DCIM 行")
    sys.exit(1)
tap(lbl, wait=4)
items = dump()
sw = [i for i in items if i["checkable"] and i["cy"] > ay and abs(i["cy"] - lbl["cy"]) < 45]
state = sw[0]["checked"] if sw else None
print("   DCIM 开关现在 =", state)
ok(state is True, "DCIM 开关已重新打开")
shot("28_switch_on_again.png")
p = find_exact(items, "完成")
if p:
    tap(p, wait=4)

print("=== C) 开关为「开」时：应再次弹窗（证明开关双向可控）===")
# 再补一个候选视频，确保 DCIM 里确实有东西可筛
sh("push", os.path.join(r"C:\Users\tony\.workbuddy\binaries\_dl\videos", "dcim_high_45m_b.mp4"),
   "/sdcard/DCIM/Camera/dcim_high_45m_d.mp4", timeout=400)
sh("shell", "chmod", "644", "/sdcard/DCIM/Camera/dcim_high_45m_d.mp4")
print("   DCIM:", " | ".join(ls("/sdcard/DCIM/Camera")))

items = launch()
scroll_top()
items = dump()
items, on_dcim = goto_dcim(items)
if not scan_and_wait(items):
    sys.exit(1)
items = wait_for(lambda i: any("是否移动到" in t for t in T(i)), timeout=220)
title = next((t for t in T(items) if "是否移动到" in t), "")
body = next((t for t in T(items) if "筛选出" in t), "")
print("   标题:", title)
print("   正文:", body)
ok(bool(title), "开关打开后恢复自动弹窗（开关双向可控）")
ok("dcim_high_45m_d.mp4" not in " ".join(ls("/sdcard/Movies/FilteredVideos")), "弹窗阶段尚未移动文件")
p = find_exact(items, "暂不")
if p:
    tap(p, wait=3)
shot("29_prompt_again.png")

# ============================================================
passed = sum(1 for c, _ in RESULTS if c)
print()
print("=" * 58)
print("  结果: %d/%d 通过" % (passed, len(RESULTS)))
for c, m in RESULTS:
    print("   %s %s" % ("[PASS]" if c else "[FAIL]", m))
print("=" * 58)
