#!/usr/bin/env python3
"""Guard against the ViewModel constructor-order crash (Osmium 2.6.0).

A `stateIn(viewModelScope, SharingStarted.Eagerly, ...)` property can run its
transform DURING the ViewModel constructor: when the settings repository is warm
(DataStore already read in this process) it emits synchronously, so the combine
lambda executes before the constructor has finished. Kotlin initialises members
in declaration order, so any non-primitive member such a lambda reads must be
declared ABOVE that property — otherwise it is still null, and the app dies with
e.g. `NullPointerException: List.isEmpty() on a null object reference`.

That is exactly the bug that made every notification tap crash: `randomOrder`
was declared ~130 lines below `sortedAccountUiList`.

This script scans the Kotlin sources for Eagerly stateIn blocks and reports
members read inside a block but declared after it. Exit code 1 on a hit.
"""
from __future__ import annotations

import pathlib
import re
import sys

SOURCE_ROOT = pathlib.Path("app/src/main/java")

MEMBER_RE = re.compile(
    r"^(?P<indent>\s{4})(?P<mods>(?:@\w+\s+)?(?:private |internal |protected |public )*)"
    r"(?P<kw>val|var)\s+(?P<name>[A-Za-z_]\w*)\s*(?::\s*(?P<type>[^=]+?))?\s*(?:=|\{|$)"
)
EAGER = "SharingStarted.Eagerly"

# JVM primitives: uninitialised they read as 0/false, never null, so a wrong
# declaration order cannot crash on them.
PRIMITIVES = ("Boolean", "Int", "Long", "Float", "Double", "Char", "Short", "Byte")


def is_primitive(type_text: str | None) -> bool:
    if not type_text:
        return False
    base = type_text.strip().rstrip("?").strip()
    return base in PRIMITIVES


def scan_file(path: pathlib.Path) -> list[str]:
    lines = path.read_text(encoding="utf-8").splitlines()
    members: list[tuple[int, str, str | None]] = []  # (line, name, type)
    for idx, line in enumerate(lines):
        m = MEMBER_RE.match(line)
        if m:
            members.append((idx, m.group("name"), m.group("type")))

    problems: list[str] = []
    for idx, line in enumerate(lines):
        if EAGER not in line:
            continue
        # Find the property this stateIn belongs to: nearest member declaration
        # at or above this line.
        owner = None
        for start, name, _type in reversed(members):
            if start <= idx:
                owner = (start, name)
                break
        if owner is None:
            continue
        start, owner_name = owner
        block = "\n".join(lines[start : idx + 1])
        for decl_line, name, type_text in members:
            if decl_line <= idx:
                continue  # declared at or above the stateIn: safely initialised
            if is_primitive(type_text):
                continue
            # Only report names actually used in the block, and ignore
            # `something.name` (a member of another object).
            if not re.search(r"(?<![.\w])" + re.escape(name) + r"(?![\w])", block):
                continue
            problems.append(
                f"{path}:{decl_line + 1}: '{name}' is read by the Eagerly stateIn "
                f"of '{owner_name}' (line {start + 1}) but declared after it "
                f"({decl_line + 1}) — move it above '{owner_name}'."
            )
    return problems


def main() -> int:
    if not SOURCE_ROOT.is_dir():
        print(f"source root not found: {SOURCE_ROOT}", file=sys.stderr)
        return 2
    all_problems: list[str] = []
    for path in sorted(SOURCE_ROOT.rglob("*.kt")):
        all_problems += scan_file(path)
    if all_problems:
        print("Eagerly-stateIn declaration-order problems:")
        for p in all_problems:
            print("  " + p)
        return 1
    print("eager stateIn field order: OK")
    return 0


if __name__ == "__main__":
    sys.exit(main())
