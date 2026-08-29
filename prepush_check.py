#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Pre-push checks: bracket balance in changed Kotlin files + R.string cross-check."""
import os, re, subprocess, sys

ROOT = os.path.dirname(os.path.abspath(__file__))

changed = subprocess.run(
    ["git", "-C", ROOT, "diff", "--name-only"], capture_output=True, text=True
).stdout.split()

kt_files = [f for f in changed if f.endswith(".kt")]

errors = 0
for f in kt_files:
    path = f"{ROOT}/{f}"
    src = open(path, encoding="utf-8").read()
    # strip strings and comments crudely. ORDER MATTERS:
    # block comments first (they can contain "//" like otpauth:// and quotes),
    # then strings (they can contain "//" like https://), then line comments.
    code = re.sub(r'"""(?:.|\n)*?"""', '""', src)
    code = re.sub(r"/\*.*?\*/", "", code, flags=re.S)
    code = re.sub(r'"(?:[^"\\]|\\.)*"', '""', code)
    code = re.sub(r"//[^\n]*", "", code)
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
