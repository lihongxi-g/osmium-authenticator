#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Pre-push checks: bracket balance in changed Kotlin files + R.string cross-check.

Bracket counting uses a single-pass scanner that drops line/block comments,
string templates, raw strings and char literals. (The previous regex-based
stripper was fooled by strings that contain comment markers, e.g. the MIME
type "*/*" or urls in strings.)
"""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.abspath(__file__))


def strip_kt(src: str) -> str:
    """Return src with comments/strings replaced, suitable for bracket counting."""
    out = []
    i, n = 0, len(src)
    state = "code"
    while i < n:
        c = src[i]
        if state == "code":
            if c == '"':
                if src.startswith('"""', i):
                    state = "raw"
                    out.append('""')
                    i += 3
                    continue
                state = "str"
                out.append('""')
                i += 1
                continue
            if c == "'":
                state = "char"
                out.append("''")
                i += 1
                continue
            if c == "/" and i + 1 < n:
                if src[i + 1] == "/":
                    state = "line"
                    i += 2
                    continue
                if src[i + 1] == "*":
                    state = "block"
                    i += 2
                    continue
            out.append(c)
            i += 1
        elif state == "str":
            if c == "\\":
                i += 2
                continue
            if c == '"':
                state = "code"
            i += 1
        elif state == "char":
            if c == "\\":
                i += 2
                continue
            if c == "'":
                state = "code"
            i += 1
        elif state == "raw":
            if src.startswith('"""', i):
                state = "code"
                i += 3
                continue
            i += 1
        elif state == "line":
            if c == "\n":
                state = "code"
                out.append(c)
            i += 1
        elif state == "block":
            if c == "*" and src.startswith("*/", i):
                state = "code"
                i += 2
                continue
            if c == "\n":
                out.append(c)
            i += 1
    return "".join(out)


changed = subprocess.run(
    ["git", "-C", ROOT, "diff", "--name-only"], capture_output=True, text=True
).stdout.split()

# Deleted files show up in the diff but cannot be read; skip them.
kt_files = [f for f in changed if f.endswith(".kt") and os.path.exists(f"{ROOT}/{f}")]

errors = 0
for f in kt_files:
    path = f"{ROOT}/{f}"
    src = open(path, encoding="utf-8").read()
    code = strip_kt(src)
    for op, cl in [("{", "}"), ("(", ")"), ("[", "]")]:
        o, c = code.count(op), code.count(cl)
        if o != c:
            print(f"IMBALANCE {f}: {op}={o} {cl}={c}")
            errors += 1

# R.string cross-check
string_names = set()
for m in re.finditer(r'name="([a-z0-9_]+)"', open(f"{ROOT}/app/src/main/res/values/strings.xml", encoding="utf-8").read()):
    string_names.add(m.group(1))

used = set()
for f in kt_files:
    src = open(f"{ROOT}/{f}", encoding="utf-8").read()
    for m in re.finditer(r"R\.string\.([A-Za-z0-9_]+)", src):
        used.add(m.group(1))

missing = sorted(n for n in used if n not in string_names)
if missing:
    print("MISSING STRINGS:", missing)
    errors += 1
else:
    print(f"strings OK: {len(used)} used, all defined")

print("BRACKETS:", "OK" if errors == 0 else "FAIL")
sys.exit(1 if errors else 0)
