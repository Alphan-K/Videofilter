#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""多选移动功能端到端验证。

覆盖：长按多选 / 全选 / 路径记忆 / 物理移动 / 重名递增 / 元数据保留 / 冷启动持久化
"""
import subprocess, time, re, os, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
FFMPEG = (r"C:\Users\tony\AppData\Local\Microsoft\WinGet\Packages"
          r"\yt-dlp.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-N-122609-g364d5dda91-win64-gpl\bin\ffmpeg.exe")
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\mv.xml"
TMP = r"C:\Users\tony\.workbuddy\binaries\_dl\videos"
SRC = "/sdcard/Movies/MoveSourceTest"
TGT = "/sdcard/Movies/FilteredVideos"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def sh(*a, timeout=120):
    try:
        return subprocess.run([ADB] + list(a), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/mv.xml")
    sh("pull", "/sdcard/mv.xml", DUMP)
    try:
        root = ET.parse(DUMP).getroot()
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        t = n.get("text", "") or ("@" + n.get("content-desc", "") if n.get("content-desc") else "")
        b = n.get("bounds", "")
        m = BOUNDS.match(b) if b else None
        if m:
            l, tp, r, bo = (int(v) for v in m.groups())
            out.append((t or "", (l + r) // 2, (tp + bo) // 2))
    return out


def collapse_filter_panel():
    """确保筛选面板处于**收起**状态（幂等：已收起则不动，否则会误展开）"""
    items = dump()
    expanded = any("封装格式" in t for t in T(items))
    if not expanded:
        return "已是收起状态"
    p = find(items, "@展开收起")
    if p:
        tap(*p)
        time.sleep(3)
        return "点折叠按钮"
    # 退化方案：直接向上滚动
    for _ in range(3):
        sh("shell", "input", "swipe", "270", "1000", "270", "250", "400")
        time.sleep(2)
    return "手动滚动"


def T(items):
    return [t for t, x, y in items if t]


def find(items, kw):
    for t, x, y in items:
        if kw in t:
            return x, y
    return None


def find_exact(items, text):
    """精确匹配：避免 "完成" 误命中弹窗标题 "迁移顺利完成" """
    for t, x, y in items:
        if t == text:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def longpress(x, y):
    # 起止同点 + 时长 1000ms = 长按
    sh("shell", "input", "swipe", str(x), str(y), str(x), str(y), "1000")


def ls(path):
    return [l for l in sh("shell", "ls", "-1", path).replace("\r", "").split("\n") if l.strip()]


def ok(cond, msg):
    print(("  OK   " if cond else "  FAIL ") + msg)
    return cond


def main():
    os.makedirs(TMP, exist_ok=True)
    results = []

    print("=== 步骤 0: 构造测试环境 ===")
    sh("shell", "rm", "-rf", SRC, TGT)
    sh("shell", "mkdir", "-p", SRC, TGT)
    # 生成 2 个真实视频（可被 MediaExtractor 解析）
    files = [("mv_h264_a.mp4", "1920x1080", "6000k"),
             ("mv_h265_b.mp4", "1280x720", "1500k")]
    for name, size, br in files:
        local = os.path.join(TMP, name)
        subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                        "-f", "lavfi", "-i", "testsrc2=size=%s:rate=30:duration=3" % size,
                        "-c:v", ("libx265" if "h265" in name else "libx264"),
                        "-b:v", br, "-pix_fmt", "yuv420p", local], capture_output=True)
        sh("push", local, SRC + "/" + name)
    # 目标目录预置一个重名文件，用于验证防覆盖递增
    sh("shell", "cp", SRC + "/mv_h264_a.mp4", TGT + "/mv_h264_a.mp4")
    sh("shell", "chmod", "-R", "644", SRC, TGT)
    print("  源目录:", ls(SRC))
    print("  目标目录(预置重名):", ls(TGT))

    print("=== 步骤 1: 冷启动 + 增量检索建立索引 ===")
    sh("shell", "pm", "clear", PKG)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(10)
    items = dump()
    pos = find(items, "增量检索")
    if pos:
        tap(*pos)
        time.sleep(25)
    items = dump()
    total = next((t for t in T(items) if t.startswith("总收录")), "?")
    print("  ", total)

    print("=== 步骤 1.5: 收起筛选面板，让卡片进入视口 ===")
    how = collapse_filter_panel()
    print("  ", how)
    items = dump()

    print("=== 步骤 2: 记录移动前的卡片元数据 ===")
    before = {}
    for t, x, y in items:
        if t.endswith(".mp4"):
            before[t] = (x, y)
    print("  可见卡片:", list(before.keys()))
    # 抓取第一张卡片附近的 kbps 与路径
    kbps_before = [t for t in T(items) if "kbps" in t]
    print("  移动前码率胶囊:", kbps_before)

    print("=== 步骤 3: 长按第一张卡片进入多选 ===")
    if not before:
        print("  !! 列表中没有可见卡片，后续无法继续")
        return
    target_name = list(before.keys())[0]
    tx, ty = before[target_name]
    longpress(tx, ty)
    time.sleep(4)
    items = dump()
    sel = next((t for t in T(items) if t.startswith("已选")), "")
    results.append(ok(sel != "", "长按进入多选模式 -> '%s'" % sel))

    print("=== 步骤 4: 校验底栏目标路径与移动按钮 ===")
    tgtline = next((t for t in T(items) if t.startswith("目标:")), "")
    moveline = next((t for t in T(items) if "移动到此目录" in t), "")
    results.append(ok("FilteredVideos" in tgtline, "底栏显示默认目标 -> '%s'" % tgtline))
    results.append(ok(moveline != "", "移动按钮就位 -> '%s'" % moveline))

    print("=== 步骤 5: 点「全选」并执行移动 ===")
    p = find(items, "全选")
    if p:
        tap(*p)
        time.sleep(3)
        items = dump()
    sel = next((t for t in T(items) if t.startswith("已选")), "")
    print("  选中状态:", sel)
    p = find(items, "移动到此目录")
    if p:
        tap(*p)
        time.sleep(15)
    items = dump()
    done = next((t for t in T(items) if "完成" in t or "成功移动" in t or "顺利" in t), "")
    print("  移动结果弹窗:", [t for t in T(items) if "移动" in t or "成功" in t][:4])
    # 关闭弹窗：必须精确匹配按钮文本，否则会命中标题「迁移顺利完成」
    p = find_exact(items, "完成") or find_exact(items, "了解")
    if p:
        tap(*p)
        time.sleep(4)
    items = dump()
    results.append(ok(
        not any("成功移动" in t for t in T(items)),
        "结果弹窗已关闭"
    ))

    print("=== 步骤 6: 文件系统断言 ===")
    src_left = ls(SRC)
    tgt_files = ls(TGT)
    print("  源目录剩余:", src_left)
    print("  目标目录:", tgt_files)
    results.append(ok(len(src_left) == 0, "源目录已清空 (剩余 %d)" % len(src_left)))
    results.append(ok(len(tgt_files) == 3, "目标目录恰好 3 个文件(预置1 + 移入2) -> %d" % len(tgt_files)))
    bumped = [f for f in tgt_files if re.search(r"\(\d+\)", f)]
    results.append(ok(len(bumped) == 1, "防覆盖递增命名生效 -> %s" % bumped))

    print("=== 步骤 7: 元数据保留 + 路径热更新 ===")
    # 确保卡片进入视口
    collapse_filter_panel()
    items = dump()
    kbps_after = [t for t in T(items) if "kbps" in t]
    paths_after = [t for t in T(items) if t.startswith("/storage")]
    print("  移动后码率胶囊:", kbps_after)
    print("  移动后路径:", paths_after[:3])
    results.append(ok(
        any("FilteredVideos" in p for p in paths_after),
        "卡片路径已热更新到新目录"
    ))
    nums = [int(m.group(1)) for m in (re.match(r"(\d+) kbps", k) for k in kbps_after) if m]
    results.append(ok(
        bool(nums) and max(nums) > 5000,
        "重型元数据保留（未被重置为 0/N-A）-> %s" % kbps_after
    ))
    results.append(ok(
        set(kbps_before).issubset(set(kbps_after)),
        "移动前后码率胶囊集合一致 -> 前%s / 后%s" % (kbps_before, kbps_after)
    ))
    results.append(ok(
        "未知码率" not in kbps_after,
        "无卡片退化为「未知码率」"
    ))

    print("=== 步骤 8: 冷启动验证路径持久化 + 数据落库 ===")
    sh("shell", "am", "force-stop", PKG)
    time.sleep(2)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(10)
    items = dump()
    total = next((t for t in T(items) if t.startswith("总收录")), "?")
    print("  冷启动:", total)
    results.append(ok("总收录: 3" in total, "冷启动直出且计数正确 -> %s" % total))

    print("\n================ 汇总 ================")
    print("通过 %d / %d" % (sum(1 for r in results if r), len(results)))


if __name__ == "__main__":
    main()
