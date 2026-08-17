#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Fix terms_body for en/zh (rewritten in v2.3.3 legal sync, different phrasing)."""
import re

BASE = "/root/osmium-audit-20260816/app/src/main/res"
FIXES = {
 "values": ("terms_body",
            "the GitHub releases API once per day for the optional update check",
            "the GitHub releases API for the optional update check"),
 "values-zh": ("terms_body",
               "（每天最多一次，不发送任何账户或设备数据）",
               "（不发送任何账户或设备数据）"),
}

for folder, (key, old, new) in FIXES.items():
    path = f"{BASE}/{folder}/strings.xml"
    content = open(path, encoding="utf-8").read()
    pattern = re.compile(
        r'(<string name="' + re.escape(key) + r'">)(.*?)(</string>)', re.DOTALL
    )
    def repl(m):
        value = m.group(2)
        assert old in value, f"{folder}: NOT FOUND: {old[:50]!r}"
        return m.group(1) + value.replace(old, new, 1) + m.group(3)
    content = pattern.sub(repl, content, count=1)
    open(path, "w", encoding="utf-8").write(content)
    print(f"OK {folder}")

print("DONE")
