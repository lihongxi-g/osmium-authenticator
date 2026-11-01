# Third-party fonts

Osmium bundles four optional interface faces. All four are licensed under the
**SIL Open Font License, Version 1.1**, which permits bundling and
redistribution inside an application as long as the copyright notice and the
licence travel with the font.

Every file below is redistributed **byte-for-byte as published** by its
project. The only change is the file name (`Name[axis].ttf` → lowercase, no
brackets), because Android resource names must be lowercase `[a-z0-9_]`.
Renaming the file is not a modification of the font software, so the Reserved
Font Names stay untouched.

| App label | File | Version | Axes | Size | Scripts |
|---|---|---|---|---|---|
| Lora | `app/src/main/res/font/lora.ttf` | 3.008 | `wght` | 212 KB | latin, latin-ext, cyrillic, cyrillic-ext, vietnamese |
| Source Serif 4 | `app/src/main/res/font/sourceserif4.ttf` | 4.004 | `opsz`, `wght` | 1181 KB | latin, latin-ext, cyrillic, cyrillic-ext, greek, vietnamese |
| Literata | `app/src/main/res/font/literata.ttf` | 3.103 | `opsz`, `wght` | 933 KB | latin, latin-ext, cyrillic, cyrillic-ext, greek, greek-ext, vietnamese |
| Crimson Pro | `app/src/main/res/font/crimsonpro.ttf` | 1.003 | `wght` | 241 KB | latin, latin-ext, vietnamese |

Measured from the shipped files: Lora 778 code points, Source Serif 4 918,
Literata 1163, Crimson Pro 689. **None of the four has CJK, Hangul or
Devanagari**, so Chinese, Japanese, Korean and Hindi text renders with the
system font glyph by glyph; Crimson Pro has **no Cyrillic** either, so Russian
text falls back to the system font while it is selected. Both facts are shown
in-app under Settings → Appearance → Font → Font notes.

## Copyright and licence

- **Lora** — Copyright 2011 The Lora Project Authors
  (<https://github.com/cyrealtype/Lora-Cyrillic>), with Reserved Font Name
  "Lora". Source: Google Fonts `ofl/lora`.
- **Source Serif 4** — Copyright 2014–2023 Adobe
  (<https://github.com/adobe-fonts/source-serif>), with Reserved Font Name
  "Source". Source: Google Fonts `ofl/sourceserif4`.
- **Literata** — Copyright 2017 The Literata Project Authors
  (<https://github.com/googlefonts/literata>). Source: Google Fonts
  `ofl/literata`.
- **Crimson Pro** — Copyright 2018 The Crimson Pro Project Authors
  (<https://github.com/googlefonts/crimson>). Source: Google Fonts
  `ofl/crimsonpro`.

Full licence texts ship inside the APK:

- `app/src/main/assets/licenses/ofl_lora.txt` (+ upstream `FONTLOG`)
- `app/src/main/assets/licenses/ofl_sourceserif4.txt`
- `app/src/main/assets/licenses/ofl_literata.txt`
- `app/src/main/assets/licenses/ofl_crimsonpro.txt`

The attribution is also visible in-app: Settings → About → Attributions →
*Bundled libraries*, one entry per face with a link to its upstream project.

## Why these four

- **Source Serif 4** — same design family as Source Han Serif, so a future CJK
  face would match it; `opsz` keeps small labels and large headings both
  comfortable.
- **Literata** — drawn for screen reading; sturdier at 11–16 sp than a book
  serif.
- **Crimson Pro** — the smallest file (241 KB) and the most restrained look, at
  the cost of Cyrillic and Greek coverage.
- **Lora** — the original bundled option: a warm, slightly calligraphic serif.

Only one face is applied at a time, and the platform font stays the default.
Verification codes ignore all of them and stay monospace.
