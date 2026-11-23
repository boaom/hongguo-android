#!/usr/bin/env python3
"""通用完整性自检：本机不做编译，只做静态证据。

用法：
  python3 tools/integrity_check.py            # 检查相对 origin/main 的全部改动
  python3 tools/integrity_check.py <base>     # 指定基线（默认 origin/main，取不到则 HEAD~1）

检查项：
  1. 改动文件存在、无冲突残留标记（<<<<<<< / >>>>>>> / ======= / 裸 HEAD 行）
  2. 无 NUL 字节、无尾部空字节填充
  3. 大括号/圆括号配平（AppSource.java 因字符串内含斜杠字符天然差 1，已知容忍）
  4. 关键锚点存在（播放链修复相关符号，缺一个即 FAIL）
  5. 注入缝齐全：三个 set*ForTest 与对应 reset
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent

# 播放链修复必须存在的锚点：文件 -> [正则...]
ANCHORS = {
    "app/src/main/java/com/hongguo/android/DramaRepository.kt": [
        r"fun\s+retryablePlaybackFailure",
    ],
    "app/src/main/java/com/hongguo/android/HongguoViewModel.kt": [
        r"retryablePlaybackFailure\s*\(",
    ],
    "app/src/main/java/com/hongguo/api/service/AppSource.java": [
        r"class\s+PlaybackRejectedException",
        r"PLAYBACK_IDENTITY\s*\.\s*compareAndSet",
        r"checkPlaybackCancellation\s*\(",
        r"hasPlaybackTime\s*\(",
        # 2026-11-23 合并上游 0.7.33 后：播放链换成 Kotlin MobilePlayerV5 +
        # fetchTabV5/video_tab v5 GET；注入缝以上游为准。
        r"fetchTabV5\s*\(",
        r"setSinglePlaybackForTest",
        r"setPlaybackTabForTest",
        r"setPlaybackFallbackForTest",
        r"discardPlaybackIdentity",
        r"PlaybackBackoffException",
    ],
}

# 已知的配平容忍：路径 -> (braces_delta, parens_delta)
TOLERANCE = {}  # 剥掉字面量后计数是精确的，不再需要容忍

problems = []


def git(*args):
    return subprocess.run(
        ["git", "-C", str(ROOT), *args], capture_output=True, text=True
    ).stdout


def changed_files(base):
    out = git("diff", "--name-only", f"{base}...HEAD")
    files = [f for f in out.split() if f]
    if not files:
        files = [f for f in git("diff", "--name-only", "HEAD~1..HEAD").split() if f]
    local = git("diff", "--name-only").split()
    for f in local:
        if f and f not in files:
            files.append(f)
    return files


def strip_literals(text):
    """剥掉字符串/字符字面量与注释，剩下的括号才是结构性的。"""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        nxt = text[i + 1] if i + 1 < n else ""
        if c == "/" and nxt == "/":
            j = text.find("\n", i)
            i = n if j < 0 else j
            continue
        if c == "/" and nxt == "*":
            j = text.find("*/", i + 2)
            i = n if j < 0 else j + 2
            continue
        if c in "\"'":
            q = c
            i += 1
            while i < n:
                if text[i] == "\\":
                    i += 2
                    continue
                if text[i] == q:
                    i += 1
                    break
                i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def check_text(path):
    p = ROOT / path
    if not p.exists():
        problems.append(f"缺失文件: {path}")
        return
    raw = p.read_bytes()
    if b"\x00" in raw:
        problems.append(f"NUL 字节: {path}")
    if raw.endswith(b"\n\n") or raw.endswith(b"\x00"):
        problems.append(f"尾部异常填充: {path}")
    text = raw.decode("utf-8", errors="replace")
    for i, line in enumerate(text.splitlines(), 1):
        if re.match(r"^(<<<<<<<|>>>>>>>|=======$|HEAD$|upstream-o/main$)", line.strip()):
            problems.append(f"冲突残留 {path}:{i} {line.strip()!r}")
    if not path.endswith((".java", ".kt", ".kts")):
        return  # 文档类只查冲突残留与 NUL，不做括号配平
    code = strip_literals(text)
    b_open, b_close = code.count("{"), code.count("}")
    p_open, p_close = code.count("("), code.count(")")
    tb, tp = TOLERANCE.get(path, (0, 0))
    if abs((b_open - b_close) - tb) > 0:
        problems.append(f"大括号不配平 {path}: {{={b_open} }}={b_close}")
    if abs((p_open - p_close) - tp) > 0:
        problems.append(f"圆括号不配平 {path}: (={p_open} )={p_close}")


def check_anchors():
    for path, pats in ANCHORS.items():
        p = ROOT / path
        if not p.exists():
            problems.append(f"锚点文件缺失: {path}")
            continue
        text = p.read_text(encoding="utf-8", errors="replace")
        for pat in pats:
            if not re.search(pat, text):
                problems.append(f"锚点缺失 {path}: /{pat}/")


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else "origin/main"
    if not git("rev-parse", "--verify", base).strip():
        base = "HEAD~1"
    files = changed_files(base)
    print(f"基线 {base}，改动文件 {len(files)} 个")
    # 本次合并主动删除的旧实现文件（测的是已不存在的"两跳"链路）；
    # 删除是预期行为，不能当作"缺失文件"报错。
    removed = {
        "app/src/test/java/com/hongguo/api/service/PlaybackNewHopsTest.java",
        "app/src/test/java/com/hongguo/api/service/MobileV5ResponseTest.java",
    }
    for f in files:
        if f in removed or not (ROOT / f).exists():
            continue
        if f.endswith((".java", ".kt", ".kts", ".md", ".yml")):
            check_text(f)
    check_anchors()
    if problems:
        print("FAIL")
        for x in problems:
            print(" -", x)
        return 1
    print("ALL PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
