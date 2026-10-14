#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""UI 自动化探针：逐个切换编码筛选芯片，读取实时结果数。
   由于 Compose FilterChip 在选中/未选中状态下宽度不同会导致整行重排，
   每次点击前都重新 dump UI 获取最新坐标。"""
import subprocess, sys, time, re, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DUMP_LOCAL = r"C:\Users\tony\.workbuddy\binaries\_dl\ui.xml"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")

CODECS = ["H.264 / AVC", "H.265 / HEVC", "AV1", "VP8", "VP9",
          "MPEG-4", "VC-1", "其他/未知"]


def sh(*args, **kw):
    return subprocess.run([ADB] + list(args), capture_output=True, text=True,
                          encoding="utf-8", errors="replace", **kw).stdout


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/ui.xml")
    sh("pull", "/sdcard/ui.xml", DUMP_LOCAL)
    root = ET.parse(DUMP_LOCAL).getroot()
    items = []
    for n in root.iter("node"):
        t = n.get("text", "")
        b = n.get("bounds", "")
        if t and b:
            m = BOUNDS.match(b)
            if not m:
                continue
            l, tp, r, bo = (int(v) for v in m.groups())
            items.append((t, (l + r) // 2, (tp + bo) // 2))
    return items


def find(items, text, ymin=0):
    for t, x, y in items:
        if t == text and y >= ymin:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def counts(items):
    for t, _, _ in items:
        if t.startswith("总收录"):
            return t
    return "?"


def codec_toggle(items):
    """返回编码区块的全选/取消全选按钮坐标（y 大于 700 的那个）"""
    for t, x, y in items:
        if t in ("全选", "取消全选") and y > 700:
            return (x, y), t
    return None, None


def get_sel(items):
    for t, _, _ in items:
        m = re.search(r"\((\d+)/8\)", t)
        if m:
            return int(m.group(1))
    return None


def clear_codec(items):
    """确保编码区为 0 选中"""
    for _ in range(4):
        n = get_sel(items)
        if n == 0:
            return items
        p, lab = codec_toggle(items)
        if not p:
            return items
        tap(*p)
        time.sleep(3)
        items = dump()
    return items


def main():
    items = dump()
    print("初始:", counts(items), " 编码选中:", get_sel(items))

    results = {}
    for codec in CODECS:
        items = clear_codec(items)
        if get_sel(items) != 0:
            results[codec] = "清空失败"
            continue
        target = find(items, codec)
        if not target:
            results[codec] = "坐标未找到 " + str([i[0] for i in items if i[2] > 700])
            continue
        tap(*target)
        time.sleep(4)
        items = dump()
        results[codec] = "选中%d -> %s" % (get_sel(items), counts(items))
        print("  %-14s -> %s" % (codec, results[codec]))

    print("\n===== 汇总（每次均从 0 选中开始，独立测试）=====")
    for k, v in results.items():
        print("%-14s %s" % (k, v))


if __name__ == "__main__":
    main()
