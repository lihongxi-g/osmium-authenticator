#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Build `app/src/main/res/font/hanserif.ttf` from upstream Noto Serif SC.

The bundled Chinese face is a *subset* of the published variable font: GB2312
(Simplified) + Big5 level-1 (Traditional) + every character the app's own
strings use + Latin/Greek/Cyrillic/punctuation. That keeps the file at ~7.5 MB
instead of the 24 MB upstream, while still rendering the whole interface
without a per-glyph fallback.

Usage (needs `fonttools` and network access):

    python3 scripts/build_han_serif_subset.py

Upstream: Google Fonts, `ofl/notoserifsc/NotoSerifSC[wght].ttf`, SIL OFL 1.1
(copyright and licence text: app/src/main/assets/licenses/ofl_notoserifsc.txt).
"""
import glob
import os
import re
import subprocess
import sys
import tempfile
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "app/src/main/res/font/hanserif.ttf")
OUT_BOLD = os.path.join(ROOT, "app/src/main/res/font/hanserif_bold.ttf")
RES = os.path.join(ROOT, "app/src/main/res")
UPSTREAM = "https://raw.githubusercontent.com/google/fonts/main/ofl/notoserifsc/NotoSerifSC%5Bwght%5D.ttf"


def fetch(url: str, dest: str) -> None:
    print(f"downloading {url}")
    req = urllib.request.Request(url, headers={"User-Agent": "curl"})
    with urllib.request.urlopen(req, timeout=120) as r, open(dest, "wb") as f:
        f.write(r.read())


def gb2312() -> str:
    """GB2312 hanzi, 6763 characters."""
    out = []
    for hi in range(0xB0, 0xF8):
        for lo in range(0xA1, 0xFF):
            try:
                out.append(bytes([hi, lo]).decode("gb2312"))
            except UnicodeDecodeError:
                pass
    return "".join(out)


def big5_level1() -> str:
    """Big5 level-1 (常用字), 5401 characters."""
    out = []
    for hi in range(0xA4, 0xC7):
        for lo in list(range(0x40, 0x7F)) + list(range(0xA1, 0xFF)):
            try:
                out.append(bytes([hi, lo]).decode("big5"))
            except UnicodeDecodeError:
                pass
    return "".join(out)


def app_characters() -> str:
    """Every character the app itself can display, from all locale strings."""
    chars = set()
    for path in glob.glob(os.path.join(RES, "values*", "strings.xml")):
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r"<string[^>]*>(.*?)</string>", text, re.S):
            chars.update(m.group(1))
    return "".join(sorted(chars))


def main() -> int:
    latin = "".join(chr(c) for c in range(0x20, 0x7F))
    latin += "".join(chr(c) for c in range(0xA0, 0x250))       # Latin-1, Ext-A, Ext-B
    latin += "".join(chr(c) for c in range(0x1E00, 0x1F00))    # Latin Extended Additional
    latin += "".join(chr(c) for c in range(0x20A0, 0x20D0))    # currency
    latin += "".join(chr(c) for c in range(0x2000, 0x2070))    # general punctuation
    cyrillic = "".join(chr(c) for c in range(0x400, 0x460))
    greek = "".join(chr(c) for c in range(0x370, 0x400))
    cjk_punct = "".join(chr(c) for c in range(0x3000, 0x3040))
    fullwidth = "".join(chr(c) for c in range(0xFF00, 0xFFF0))

    charset = "".join(sorted(set(
        app_characters() + gb2312() + big5_level1()
        + latin + cyrillic + greek + cjk_punct + fullwidth
    )))

    with tempfile.TemporaryDirectory() as tmp:
        src = os.path.join(tmp, "NotoSerifSC[wght].ttf")
        fetch(UPSTREAM, src)
        text_file = os.path.join(tmp, "charset.txt")
        with open(text_file, "w", encoding="utf-8") as f:
            f.write(charset)
        print(f"subsetting {len(charset)} characters -> {OUT}")
        subset = os.path.join(tmp, "subset.ttf")
        subprocess.run([
            sys.executable, "-m", "fontTools.subset", src,
            f"--text-file={text_file}",
            "--layout-features=*",
            "--drop-tables+=DSIG",
            f"--output-file={subset}",
        ], check=True)
        # Upstream defaults to the ExtraLight instance; pin the axis so the
        # font's own default is Regular 400. If a platform ever ignores the
        # variation settings, the text still renders at a normal weight instead
        # of hairline-thin.
        # Ship fixed instances instead of a variable file: axis settings were
        # silently ignored by the platform, so the weight slider did nothing.
        print("instancing wght=400 (regular) and wght=700 (bold)")
        subprocess.run([
            sys.executable, "-m", "fontTools.varLib.instancer", subset,
            "wght=400", "-o", OUT,
        ], check=True)
        subprocess.run([
            sys.executable, "-m", "fontTools.varLib.instancer", subset,
            "wght=700", "-o", OUT_BOLD,
        ], check=True)

    print(f"done: {os.path.getsize(OUT) / 1024 / 1024:.2f} MB regular, "
          f"{os.path.getsize(OUT_BOLD) / 1024 / 1024:.2f} MB bold")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
