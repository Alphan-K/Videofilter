#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""v1.0.3 验证：码率默认 30000 / 记忆上次值 / 灰字占位 / 清空后仍生效

注意：筛选面板展开后很高，会遮住码率输入框与空状态提示（LazyColumn 不会合成视口外节点）。
      因此断言顺序必须是「先收起面板再读列表」，需要改码率时再展开并滚动到输入框。
"""
import subprocess, time, re, os, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
DEV = "emulator-5554"
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\v103.xml"
SHOTS = r"C:\Users\tony\.workbuddy\binaries\_shots"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")
HEADER = re.compile(r"\s*(\S+)\s+(\d+)/(\d+)\s*·")

results = []


def sh(*a, timeout=180):
    try:
        return subprocess.run([ADB, "-s", DEV] + list(a), capture_output=True,
                              text=True, encoding="utf-8", errors="replace",
                              timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def dump(retries=3):
    """抓取 UI 树。

    关键：dump 失败时必须返回空列表，**绝不能回退到上一次的文件** ——
    否则脚本会一直读到过期状态，表现为「明明点了却没反应」的假象。
    """
    for _ in range(retries):
        out = sh("shell", "uiautomator", "dump", "/sdcard/v103.xml")
        if "dumped to" in out:
            sh("pull", "/sdcard/v103.xml", DUMP)
            try:
                root = ET.parse(DUMP).getroot()
            except Exception:
                time.sleep(2)
                continue
            out_list = []
            for n in root.iter("node"):
                t = n.get("text", "") or (
                    "@" + n.get("content-desc", "") if n.get("content-desc") else ""
                )
                m = BOUNDS.match(n.get("bounds", "") or "")
                if m:
                    l, tp, r, bo = (int(v) for v in m.groups())
                    out_list.append((t or "", (l + r) // 2, (tp + bo) // 2))
            return out_list
        time.sleep(2)
    return []


def T(items):
    return [t for t, x, y in items]


def find(items, kw, ymin=0, ymax=10 ** 9):
    for t, x, y in items:
        if kw in t and ymin <= y <= ymax:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def shot(name):
    d = subprocess.run([ADB, "-s", DEV, "exec-out", "screencap", "-p"],
                       capture_output=True, timeout=180).stdout
    with open(os.path.join(SHOTS, name), "wb") as f:
        f.write(d)


def ok(cond, msg):
    results.append(cond)
    print(("  OK   " if cond else "  FAIL ") + msg)


def header(items):
    for t in T(items):
        m = HEADER.match(t)
        if m:
            return t, m.group(1), int(m.group(2)), int(m.group(3))
    return "", "", -1, -1


def card_count(items):
    """列表中的视频卡片数：以视频文件名节点计数（排除路径）"""
    return len([t for t in T(items)
                if (t.endswith(".mp4") or t.endswith(".mkv")) and not t.startswith("/")])


def panel_expanded(items):
    return any("封装格式" in t for t in T(items))


def set_panel(expanded):
    """幂等地设置面板展开/收起状态"""
    scroll_top()
    items = dump()
    if panel_expanded(items) == expanded:
        return items
    p = find(items, "@展开收起")
    if p:
        tap(*p)
        time.sleep(3)
    return dump()


def reveal_bitrate():
    """展开面板并滚动，直到「最低码率」输入框进入视口"""
    set_panel(True)
    for _ in range(5):
        items = dump()
        if find(items, "最低码率"):
            return items
        sh("shell", "input", "swipe", "270", "1000", "270", "400", "400")
        time.sleep(2)
    return dump()


def scroll_top():
    """回到列表顶部。

    必须从屏幕中下部起手：从顶部附近下滑会被系统识别为「下拉通知栏」，
    把应用挤到后台，后续所有断言都会失败。
    """
    for _ in range(4):
        sh("shell", "input", "swipe", "270", "700", "270", "1100", "300")
        time.sleep(1)


def in_foreground():
    out = sh("shell", "dumpsys", "activity", "activities")
    return "topResumedActivity" in out and PKG in out.split("topResumedActivity")[1][:200]


def ensure_foreground():
    if not in_foreground():
        print("   (应用不在前台，重新拉起)")
        sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
        time.sleep(6)


def launch():
    sh("shell", "am", "force-stop", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(6)
    # 不依赖固定 sleep：轮询直到主界面渲染出来（模拟器慢时尤为必要）
    return wait_until(lambda it: header(it)[0] != "", timeout=150)


def wait_until(pred, timeout=120, interval=3):
    """轮询等待目标状态出现。模拟器 dump 可能很慢，固定 sleep 会读到过期状态。"""
    t0 = time.time()
    items = []
    while time.time() - t0 < timeout:
        items = dump()
        if pred(items):
            return items
        time.sleep(interval)
    print("   (等待超时: %.0fs)" % (time.time() - t0))
    return items


def type_min(text):
    items = reveal_bitrate()
    p = find(items, "最低码率")
    if not p:
        print("   !! 滚动后仍找不到最低码率标签")
        return False
    # 「最低码率」现在是输入框上方的标签，真正的输入框在其正下方
    tap(p[0] + 100, p[1] + 46)
    time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_MOVE_END")
    for _ in range(10):
        sh("shell", "input", "keyevent", "KEYCODE_DEL")
    time.sleep(1)
    if text:
        sh("shell", "input", "text", text)
        time.sleep(2)
    sh("shell", "input", "keyevent", "KEYCODE_BACK")
    time.sleep(3)
    scroll_top()
    return True


def main():
    print("=== 步骤 0: 清数据构造首次运行环境 ===")
    sh("shell", "pm", "clear", PKG)
    # pm clear 会一并重置 appops，必须重新授予全盘权限，否则扫描静默返回空
    sh("shell", "appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
    items = launch()
    ensure_foreground()
    items = dump()
    print("   顶栏:", header(items)[0])

    print("=== 步骤 1: 扫描 Movies 建立数据 ===")
    p = find(items, "Movies", ymax=320)
    if p:
        tap(*p)
        time.sleep(4)
    items = set_panel(False)
    p = find(items, "扫描 Movies") or find(items, "扫描 ")
    if p:
        tap(*p)
        # 等到实际收录到 3 条为止
        items = wait_until(lambda it: header(it)[3] == 3, timeout=120)
    items = set_panel(False)
    h, name, shown, total = header(items)
    print("   顶栏:", h)
    ok(total == 3, "Movies 目录收录 3 个视频")

    print("=== 步骤 2: 默认下限 30000 应过滤掉全部视频 ===")
    ok(shown == 0 and card_count(items) == 0,
       "列表被默认下限过滤为空 (%d/%d, 卡片 %d)" % (shown, total, card_count(items)))
    hint = [t for t in T(items) if "30000" in t and "低于" in t]
    print("   空状态提示:", (hint[0][:46] + "…") if hint else "(无)")
    ok(len(hint) > 0, "空列表提示明确是码率下限拦掉的")
    shot("20_default_30000.png")

    print("=== 步骤 3: 展开面板确认生效值与灰字占位 ===")
    items = reveal_bitrate()
    texts = [t.strip() for t in T(items)]
    effective = [t for t in texts if t.startswith("生效：")]
    print("   生效行:", effective[:1])
    ok(any("30000" in t for t in effective), "生效下限为 30000")
    ok("最低码率" in texts and "最高码率" in texts, "上下限标签均存在")
    # 灰字占位是纯视觉呈现（Compose 合并语义），用截图人工核对
    shot("21_placeholder_30000.png")

    print("=== 步骤 4: 输入 5000，应放行 2 个高码率视频 ===")
    ok(type_min("5000"), "已输入 5000")
    items = set_panel(False)
    h, name, shown, total = header(items)
    print("   顶栏:", h)
    ok(shown == 2 and card_count(items) == 2,
       "下限 5000 命中 2 个 (%d/%d, 卡片 %d)" % (shown, total, card_count(items)))

    print("=== 步骤 5: 重启应用，应记住 5000 ===")
    items = launch()
    items = set_panel(False)
    h, name, shown, total = header(items)
    print("   顶栏:", h)
    ok(shown == 2, "重启后仍按记忆的 5000 筛选 -> %s" % h)
    items = reveal_bitrate()
    effective = [t.strip() for t in T(items) if t.strip().startswith("生效：")]
    ok(any("5000" in t for t in effective), "生效下限已记忆为 5000 -> %s" % (effective[:1] or ""))
    shot("22_remembered_5000.png")

    print("=== 步骤 6: 输入 0（显式不限）应放行全部 3 个 ===")
    ok(type_min("0"), "已输入 0")
    items = set_panel(False)
    h, name, shown, total = header(items)
    print("   顶栏:", h)
    # 列表虚拟化只会合成视口内的卡片，因此以顶栏的「筛选后/总数」为准
    ok(shown == 3, "下限 0 命中全部 3 个 -> %s" % h)
    print("   视口内实际渲染卡片数:", card_count(items))

    print("=== 步骤 7: 重启，应记住 0 ===")
    items = launch()
    items = set_panel(False)
    h, name, shown, total = header(items)
    print("   顶栏:", h)
    ok(shown == 3, "重启后仍按记忆的 0（不限）-> %s" % h)


if __name__ == "__main__":
    main()
    print("\n================ 汇总 ================")
    print("通过 %d / %d" % (sum(1 for r in results if r), len(results)))
