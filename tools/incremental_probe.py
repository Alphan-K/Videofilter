#!/usr/bin/env python
# -*- coding: utf-8 -*-
"""增量更新端到端验证：
   1) 清空数据后冷启动 -> 应为 0（无残留）
   2) 首次增量检索   -> 全解析 4 个
   3) 再次增量检索   -> 跳过 4 个、解析 0（核心增量能力）
   4) 杀进程冷启动   -> 不扫描即直出 4（Room 持久化 + 冷启动直出）
   5) 新增 1 个视频  -> 仅解析 1 个、跳过 4 个
   6) 删除 1 个视频  -> 清理 1 条
"""
import subprocess, time, re, sys, os, xml.etree.ElementTree as ET

ADB = r"C:\Users\tony\.workbuddy\binaries\android-sdk\platform-tools\adb.exe"
FFMPEG = (r"C:\Users\tony\AppData\Local\Microsoft\WinGet\Packages"
          r"\yt-dlp.FFmpeg_Microsoft.Winget.Source_8wekyb3d8bbwe"
          r"\ffmpeg-N-122609-g364d5dda91-win64-gpl\bin\ffmpeg.exe")
PKG = "com.videofilter.app"
DUMP = r"C:\Users\tony\.workbuddy\binaries\_dl\inc.xml"
TMPDIR = r"C:\Users\tony\.workbuddy\binaries\_dl\videos"
BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def sh(*a, timeout=90):
    try:
        return subprocess.run([ADB] + list(a), capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=timeout).stdout
    except subprocess.TimeoutExpired:
        return ""


def dump():
    sh("shell", "uiautomator", "dump", "/sdcard/inc.xml")
    sh("pull", "/sdcard/inc.xml", DUMP)
    try:
        root = ET.parse(DUMP).getroot()
    except Exception:
        return []
    out = []
    for n in root.iter("node"):
        t, b = n.get("text", ""), n.get("bounds", "")
        m = BOUNDS.match(b) if b else None
        if m:
            l, tp, r, bo = (int(v) for v in m.groups())
            out.append((t or "", (l + r) // 2, (tp + bo) // 2))
    return out


def texts(items):
    return [t for t, x, y in items if t]


def find(items, kw):
    for t, x, y in items:
        if kw in t:
            return x, y
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def total_line(items):
    for t in texts(items):
        if t.startswith("总收录"):
            return t
    return "?"


def stats_line(items):
    """提取 Diff 统计条：磁盘 N · 跳过未变更 N · 解析 N · 成功 N · 清理 N"""
    for t in texts(items):
        if t.startswith("磁盘 "):
            return t
    return ""


def header_line(items):
    for t in texts(items):
        if "扫描完成" in t or "重建完成" in t:
            return t
    return ""


def wait_idle(max_wait=180):
    """等待扫描结束（出现统计条或按钮回到增量检索）"""
    t0 = time.time()
    while time.time() - t0 < max_wait:
        items = dump()
        if stats_line(items):
            return items
        time.sleep(5)
    return dump()


def run_scan(items, label):
    pos = find(items, "增量检索")
    if not pos:
        print("  !! 找不到「增量检索」按钮")
        return items
    tap(*pos)
    items = wait_idle()
    print("  %-18s -> %s" % (label, stats_line(items) or "(无统计条)"))
    return items


def restart_app():
    sh("shell", "am", "force-stop", PKG)
    time.sleep(2)
    sh("shell", "am", "start", "-n", PKG + "/.MainActivity")
    time.sleep(10)
    return dump()


def make_video(path, size="854x480", secs=2):
    subprocess.run([FFMPEG, "-y", "-hide_banner", "-loglevel", "error",
                    "-f", "lavfi", "-i", "testsrc2=size=%s:rate=30:duration=%d" % (size, secs),
                    "-c:v", "libx264", "-b:v", "2000k", "-pix_fmt", "yuv420p", path],
                   capture_output=True)


def main():
    os.makedirs(TMPDIR, exist_ok=True)

    print("=== 步骤 1: 清除应用数据后冷启动 ===")
    sh("shell", "pm", "clear", PKG)
    items = restart_app()
    print("  冷启动(空库):", total_line(items))

    print("=== 步骤 2: 首次增量检索（预期全解析 4）===")
    items = run_scan(items, "首次增量")

    print("=== 步骤 3: 再次增量检索（预期跳过 4 / 解析 0）===")
    items = run_scan(items, "第二次增量")

    print("=== 步骤 4: 杀进程冷启动（预期不扫描即直出 4）===")
    items = restart_app()
    print("  冷启动直出:", total_line(items))

    print("=== 步骤 5: 新增 1 个视频后再增量（预期解析 1 / 跳过 4）===")
    local = os.path.join(TMPDIR, "video_new_h264.mp4")
    make_video(local)
    sh("push", local, "/sdcard/Movies/video_new_h264.mp4")
    sh("shell", "chmod", "644", "/sdcard/Movies/video_new_h264.mp4")
    items = run_scan(items, "新增后增量")
    print("  总收录:", total_line(items))

    print("=== 步骤 6: 删除 1 个视频后再增量（预期清理 1）===")
    sh("shell", "rm", "-f", "/sdcard/Movies/video_new_h264.mp4")
    items = run_scan(items, "删除后增量")
    print("  总收录:", total_line(items))


if __name__ == "__main__":
    main()
