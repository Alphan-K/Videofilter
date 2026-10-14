#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""截图：进入多选态，采集顶栏/底栏/移动结果弹窗的视觉证据"""
import subprocess, time, re, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\cap.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def sh(*a):
    return subprocess.run([ADB] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace", timeout=120).stdout


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/cap.xml")
    sh("pull", "/sdcard/cap.xml", DUMP)
    root = ET.parse(DUMP).getroot()
    out = []
    for n in root.iter("node"):
        t = n.get("text", "") or ("@" + n.get("content-desc", "") if n.get("content-desc") else "")
        m = BOUNDS.match(n.get("bounds", "") or "")
        if m:
            l, tp, r, bo = (int(v) for v in m.groups())
            out.append((t or "", (l + r) // 2, (tp + bo) // 2))
    return out


def shot(name):
    sh("exec-out", "screencap", "-p")  # 预热
    data = subprocess.run([ADB, "exec-out", "screencap", "-p"],
                          capture_output=True, timeout=120).stdout
    with open(SHOTS + "\\" + name, "wb") as f:
        f.write(data)
    print("  已保存", name, len(data), "bytes")


def find(items, kw):
    for t, x, y in items:
        if kw in t:
            return x, y
    return None


def main():
    # 复位应用
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(10)
    items = dump()

    # 收起筛选面板
    if any("封装格式" in t for t in (i[0] for i in items)):
        p = find(items, "@展开收起")
        if p:
            sh("shell", "input", "tap", str(p[0]), str(p[1]))
            time.sleep(3)
    items = dump()

    # 长按第一张卡片进入多选
    card = None
    for t, x, y in items:
        if t.startswith("@") and t.endswith(".mp4"):
            card = (x, y)
            break
    if not card:
        for t, x, y in items:
            if t.endswith(".mp4") and t.startswith("mv_"):
                card = (x, y)
                break
    if card:
        sh("shell", "input", "swipe", str(card[0]), str(card[1]),
           str(card[0]), str(card[1]), "1000")
        time.sleep(4)
        shot("13_selection_mode.png")
        items = dump()
        print("  顶栏:", [t for t, x, y in items if t.startswith("已选")])
        print("  底栏:", [t for t, x, y in items if t.startswith("目标:") or "移动到此目录" in t])
    else:
        print("  未找到卡片")


if __name__ == "__main__":
    main()
