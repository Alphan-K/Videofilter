# -*- coding: utf-8 -*-
"""v1.0.4 验证：扫描完成后自动询问移动 + 按目录独立开关。

沿用本项目已固化的测试铁律：
- 所有 adb 命令显式 -s emulator-5554
- dump() 失败返回空并重试，绝不回退到旧文件
- 文本匹配用精确匹配（避免 "移动" 命中 "移动到此目录"）
- 开关型控件点击前先读当前状态（幂等）
- 断定「没弹窗」之前，必须先证明「有候选」——否则测试是空转的
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
DUMP_LOCAL = r"C:\Users\tony\.workbuddy\binaries\_dl\v104b.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
VIDEOS = r"C:\Users\tony\.workbuddy\binaries\_dl\videos"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

RESULTS = []


def sh(*args, timeout=300):
    return subprocess.run(
        [ADB, "-s", DEV] + list(args),
        capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=timeout
    ).stdout


def dump():
    """[{text, cls, cx, cy, top, bottom, checkable, checked}]；失败重试，绝不回退旧文件"""
    for _ in range(6):
        if "dumped to" not in sh("shell", "uiautomator", "dump", "/sdcard/v104b.xml"):
            time.sleep(2)
            continue
        sh("pull", "/sdcard/v104b.xml", DUMP_LOCAL)
        try:
            root = ET.parse(DUMP_LOCAL).getroot()
        except Exception:
            time.sleep(2)
            continue
        rows = []
        for n in root.iter("node"):
            text = n.get("text", "") or (
                "@" + n.get("content-desc", "") if n.get("content-desc") else ""
            )
            m = BOUNDS.match(n.get("bounds", "") or "")
            if not m:
                continue
            l, t, r, b = (int(v) for v in m.groups())
            rows.append({
                "text": text,
                "cls": n.get("class", ""),
                "cx": (l + r) // 2, "cy": (t + b) // 2,
                "top": t, "bottom": b,
                "checkable": n.get("checkable") == "true",
                "checked": n.get("checked") == "true",
            })
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


def anchor_y(items, label):
    i = find_exact(items, label)
    return i["cy"] if i else None


def tap(item, wait=3):
    sh("shell", "input", "tap", str(item["cx"]), str(item["cy"]))
    time.sleep(wait)


def tap_xy(x, y, wait=3):
    sh("shell", "input", "tap", str(int(x)), str(int(y)))
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


def wait_for(pred, timeout=200, interval=3):
    t0 = time.time()
    items = []
    while time.time() - t0 < timeout:
        items = dump()
        if items and pred(items):
            return items
        time.sleep(interval)
    return items


def ls(path):
    return sh("shell", "ls", path).replace("\r", "").strip().split("\n")


def push(local_name, remote_name, subdir="DCIM/Camera"):
    sh("push", os.path.join(VIDEOS, local_name),
       "/sdcard/%s/%s" % (subdir, remote_name), timeout=400)
    sh("shell", "chmod", "644", "/sdcard/%s/%s" % (subdir, remote_name))


def scan_dcim(items):
    p = find_exact(items, "扫描 DCIM") or find_contains(items, "扫描 DCIM")
    if not p:
        print("   !! 找不到「扫描 DCIM」，当前文本:", T(items)[:14])
        return False
    tap(p, wait=1)
    return True


def open_settings(items):
    scroll_top()
    items = dump()
    p = find_exact(items, "@自动询问设置")
    if not p:
        print("   !! 找不到设置入口，当前文本:", T(items)[:14])
        return None
    tap(p, wait=4)
    return dump()


def settings_rows(items):
    """从设置面板中解析出 {目录名: (标签节点, 开关节点)}"""
    a = anchor_y(items, "扫描后自动询问移动")
    if a is None:
        return a, {}
    switches = [i for i in items if i["checkable"] and i["cy"] > a]
    rows = {}
    for name in ("DCIM", "Pictures", "Movies", "Download", "其他"):
        label = find_exact(items, name, ymin=a)
        if not label:
            continue
        near = [s for s in switches if abs(s["cy"] - label["cy"]) < 45]
        rows[name] = (label, near[0] if near else None)
    return a, rows


# ============================================================
print("=== 步骤 0: 准备测试介质（必须真正 > 30000 kbps 才会通过默认下限）===")
sh("shell", "mkdir", "-p", "/sdcard/DCIM/Camera")
sh("shell", "rm", "-f", "/sdcard/DCIM/Camera/dcim_high_40m_a.mp4",
   "/sdcard/DCIM/Camera/dcim_high_45m_b.mp4", "/sdcard/DCIM/Camera/dcim_high_40m_c.mp4")
push("dcim_high_40m_a.mp4", "dcim_high_40m_a.mp4")
push("dcim_high_45m_b.mp4", "dcim_high_45m_b.mp4")
print("   DCIM:", " | ".join(ls("/sdcard/DCIM/Camera")))

print("=== 步骤 1: 清数据构造首次运行（DCIM 开关默认应为开启）===")
sh("shell", "pm", "clear", PKG)
time.sleep(2)
sh("shell", "appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
sh("shell", "settings", "put", "global", "hide_error_dialogs", "1")
sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
items = wait_for(lambda i: header(i) != "", timeout=120)
print("   顶栏:", header(items))
ok("0/0" in header(items), "首次运行列表为空")

print("=== 步骤 2: 扫描 DCIM ===")
scan_dcim(items)

print("=== 步骤 3: 等待自动询问弹窗（无需任何手动点击）===")
items = wait_for(lambda i: any("是否移动到" in t for t in T(i)), timeout=220)
title = next((t for t in T(items) if "是否移动到" in t), "")
body = next((t for t in T(items) if "扫描完成" in t and "筛选出" in t), "")
print("   标题:", title)
print("   正文:", body)
ok(bool(title), "扫描结束后自动弹出移动询问")
ok("FilteredVideos" in title, "标题写明目标目录 -> %s" % title)
ok("DCIM" in body and "2 个" in body, "正文指出 DCIM 筛出 2 个 -> %s" % body)
hdr = header(items)
print("   顶栏:", hdr)
ok("2/2" in hdr, "确有 2 个候选通过筛选（否则「不弹窗」的断言会变成空转）")
shot("24_auto_prompt.png")

print("=== 步骤 4: 弹窗内的就地开关应为「开」 ===")
dlg_sw = [i for i in items if i["checkable"] and i["cy"] > 300]
print("   弹窗内开关数:", len(dlg_sw), "状态:", [i["checked"] for i in dlg_sw])
ok(len(dlg_sw) >= 1 and any(i["checked"] for i in dlg_sw), "弹窗提供开关且默认开启")

print("=== 步骤 5: 点「暂不」应关闭弹窗且不移动文件 ===")
p = find_exact(items, "暂不")
ok(p is not None, "找到「暂不」按钮")
if p:
    tap(p, wait=4)
items = dump()
ok(not any("是否移动到" in t for t in T(items)), "弹窗已关闭")
left = ls("/sdcard/DCIM/Camera")
print("   DCIM:", " | ".join(left))
ok(any("dcim_high_40m_a.mp4" in x for x in left), "「暂不」未移动任何文件")

print("=== 步骤 6: 再次扫描 DCIM，这次点「移动」 ===")
scan_dcim(items)
items = wait_for(lambda i: any("是否移动到" in t for t in T(i)), timeout=220)
p = find_exact(items, "移动")
ok(p is not None, "弹窗中「移动」按钮存在")
if p:
    tap(p, wait=4)
items = wait_for(lambda i: any("成功移动" in t or "迁移" in t for t in T(i)), timeout=220)
print("   结果:", [t for t in T(items) if "迁移" in t or "成功移动" in t or "无需移动" in t][:4])
shot("25_move_result.png")
ok(any("成功移动" in t for t in T(items)), "给出移动结果回执")
p = find_exact(items, "完成")
if p:
    tap(p, wait=4)

print("=== 步骤 7: 文件系统断言 ===")
dcim_left = ls("/sdcard/DCIM/Camera")
tgt = ls("/sdcard/Movies/FilteredVideos")
print("   DCIM:", " | ".join(dcim_left))
print("   目标:", " | ".join(tgt))
ok(not any("dcim_high" in x for x in dcim_left), "两个文件均已离开 DCIM")
ok(any("dcim_high_40m_a.mp4" in x for x in tgt), "h264 文件已落到 Movies/FilteredVideos")
ok(any("dcim_high_45m_b.mp4" in x for x in tgt), "hevc 文件已落到 Movies/FilteredVideos")

print("=== 步骤 8: 打开按目录开关设置面板 ===")
items = open_settings(items)
if items is None:
    sys.exit(1)
a, rows = settings_rows(items)
print("   面板标题 anchor y =", a)
for name, (label, sw) in rows.items():
    print("     %-9s 开关=%s" % (name, sw["checked"] if sw else "(未找到)"))
ok(a is not None, "设置面板已打开")
ok(len(rows) == 5, "5 个目录入口全部识别到 (%d)" % len(rows))
ok(rows.get("DCIM", (None, None))[1] and rows["DCIM"][1]["checked"], "DCIM 开关为「开」")
others = [n for n in ("Pictures", "Movies", "Download", "其他")
          if rows.get(n, (None, None))[1] and rows[n][1]["checked"]]
ok(not others, "其余目录默认为「关」 -> %s" % (others or "全部关闭"))
shot("26_auto_move_settings.png")

print("=== 步骤 9: 关掉 DCIM 开关（写入应持久化）===")
label, sw = rows["DCIM"]
tap(label, wait=4)
items = dump()
_, rows2 = settings_rows(items)
still_on = rows2.get("DCIM", (None, None))[1]
print("   DCIM 开关现在 =", still_on["checked"] if still_on else "(未找到)")
ok(still_on is not None and not still_on["checked"], "DCIM 开关已关闭")
p = find_exact(items, "完成")
if p:
    tap(p, wait=4)

print("=== 步骤 10: 补一个候选视频，重启应用（验证开关跨进程持久化）===")
push("dcim_high_40m_a.mp4", "dcim_high_40m_c.mp4")
print("   DCIM:", " | ".join(ls("/sdcard/DCIM/Camera")))
sh("shell", "am", "force-stop", PKG)
sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
items = wait_for(lambda i: header(i) != "", timeout=120)
scroll_top()
items = dump()
scan_dcim(items)

print("=== 步骤 11: DCIM 开关已关 -> 有候选也不应弹窗 ===")
items = wait_for(lambda i: any("扫描完成" in t for t in T(i)), timeout=220)
time.sleep(5)
items = dump()
hdr = header(items)
has_prompt = any("是否移动到" in t for t in T(items))
print("   顶栏:", hdr)
print("   结果条:", [t for t in T(items) if "扫描完成" in t][:1])
ok("1/1" in hdr, "DCIM 确有 1 个候选通过筛选 -> %s" % hdr)
ok(not has_prompt, "开关关闭后不再自动弹窗（且设置已跨进程持久化）")
shot("27_no_prompt_after_off.png")

# ============================================================
passed = sum(1 for c, _ in RESULTS if c)
print()
print("=" * 58)
print("  结果: %d/%d 通过" % (passed, len(RESULTS)))
for c, m in RESULTS:
    print("   %s %s" % ("[PASS]" if c else "[FAIL]", m))
print("=" * 58)
