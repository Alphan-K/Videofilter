#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""码率范围筛选探针：验证「只填下限 / 只填上限 / 双填 / 非法区间」四种情况。
   基准（来自 ffprobe + 应用兜底公式）：
     4515 / 1208 / 783 / 311 kbps
"""
import subprocess, time, re, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\ui2.xml"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def sh(*a):
    return subprocess.run([ADB] + list(a), capture_output=True, text=True,
                          encoding="utf-8", errors="replace").stdout


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/ui2.xml")
    sh("pull", "/sdcard/ui2.xml", DUMP)
    root = ET.parse(DUMP).getroot()
    out = []
    for n in root.iter("node"):
        t, b = n.get("text", ""), n.get("bounds", "")
        m = BOUNDS.match(b) if b else None
        if m:
            l, tp, r, bo = (int(v) for v in m.groups())
            out.append((t, n.get("class", ""), (l + r) // 2, (tp + bo) // 2))
    return out


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def find(items, text, exact=True):
    for t, c, x, y in items:
        if (t == text) if exact else (text in t):
            return x, y
    return None


def find_field(items, label):
    """找到 label 对应的输入框（EditText）"""
    for t, c, x, y in items:
        if c == "android.widget.EditText" and label in (t or ""):
            return x, y
    # 退化：用标签坐标
    return find(items, label)


def count(items):
    for t, c, x, y in items:
        if t.startswith("总收录"):
            return t
    return "?"


def err(items):
    for t, c, x, y in items:
        if "不能大于" in t:
            return t
    return ""


def clear_field(items, label):
    pos = find_field(items, label)
    if not pos:
        print("   !! 找不到输入框", label)
        return items
    tap(*pos); time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(12):
        sh("shell", "input", "keyevent", "KEYCODE_DEL")
    time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(2)
    return dump()


def type_into(items, label, text):
    pos = find_field(items, label)
    if not pos:
        print("   !! 找不到输入框", label)
        return items
    tap(*pos); time.sleep(2)
    sh("shell", "input", "text", text); time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    return dump()


def get_sel(items):
    for t, c, x, y in items:
        m = re.search(r"\((\d+)/8\)", t or "")
        if m:
            return int(m.group(1))
    return None


def select_all_codecs(items):
    """确保编码区 8/8 全选"""
    if get_sel(items) == 8:
        return items
    for t, c, x, y in items:
        if t == "全选" and y > 500:
            tap(x, y)
            time.sleep(4)
            return dump()
    return items


def main():
    items = dump()
    print("起始:", count(items))
    items = select_all_codecs(items)
    print("编码全选后:", count(items))
    # 恢复码率为空
    items = clear_field(items, "最低码率")
    items = clear_field(items, "最高码率")
    print("码率清空后:", count(items))
    items = select_all_codecs(items)

    cases = [
        ("仅下限 500", [("最低码率", "500")], "预期 3 (4515,1208,783)"),
        ("仅上限 400", [("最高码率", "400")], "预期 1 (311)"),
        ("区间 300~1000", [("最低码率", "300"), ("最高码率", "1000")], "预期 2 (783,311)"),
        ("非法: 下限5000>上限100", [("最低码率", "5000"), ("最高码率", "100")], "预期报错+放行4"),
    ]
    for name, actions, expect in cases:
        items = select_all_codecs(items)
        items = clear_field(items, "最低码率")
        items = clear_field(items, "最高码率")
        for label, txt in actions:
            items = type_into(items, label, txt)
        items = dump()
        print("%-24s -> %-28s | %s | 错误提示:%s" % (name, count(items), expect, err(items) or "无"))


if __name__ == "__main__":
    main()
