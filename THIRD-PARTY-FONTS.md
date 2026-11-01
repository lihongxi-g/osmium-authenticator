# Third-party fonts

Osmium bundles three optional interface faces. All three are licensed under the
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
| Lora | `app/src/main/res/font/lora.ttf` | 3.008 | `wght` | 207 KB | latin, latin-ext, cyrillic, cyrillic-ext, vietnamese |
| Manrope | `app/src/main/res/font/manrope.ttf` | 4.505 | `wght` (200–800) | 160 KB | latin, latin-ext, cyrillic, cyrillic-ext, greek, vietnamese |
| Literata | `app/src/main/res/font/literata.ttf` | 3.103 | `opsz`, `wght` | 932 KB | latin, latin-ext, cyrillic, cyrillic-ext, greek, greek-ext, vietnamese |

Measured from the shipped files: Lora 778 code points, Manrope 678, Literata
1163. **None of the three has CJK, Hangul or Devanagari**, so Chinese,
Japanese, Korean and Hindi text renders with the system font glyph by glyph —
that fact is shown in-app under Settings → Appearance → Font → Font notes.

Manrope's variable file defaults to ExtraLight (200), so the app pins every
weight explicitly (400/500/600/700) when it declares the family; a plain
variable-font binding would render the whole interface hairline-thin.

## Copyright and licence

- **Lora** — Copyright 2011 The Lora Project Authors
  (<https://github.com/cyrealtype/Lora-Cyrillic>), with Reserved Font Name
  "Lora". Source: Google Fonts `ofl/lora`.
- **Manrope** — Copyright 2018 The Manrope Project Authors
  (<https://github.com/googlefonts/manrope>). Source: Google Fonts
  `ofl/manrope`.
- **Literata** — Copyright 2017 The Literata Project Authors
  (<https://github.com/googlefonts/literata>). Source: Google Fonts
  `ofl/literata`.

Full licence texts ship inside the APK:

- `app/src/main/assets/licenses/ofl_lora.txt` (+ upstream `FONTLOG`)
- `app/src/main/assets/licenses/ofl_manrope.txt`
- `app/src/main/assets/licenses/ofl_literata.txt`

The attribution is also visible in-app: Settings → About → Attributions →
*Bundled libraries*, one entry per face with a link to its upstream project.

## Why these three

- **Lora** — a warm, slightly calligraphic serif.
- **Manrope** — the sans-serif option: compact (160 KB), modern geometric
  letterforms, and Cyrillic + Greek included, so Russian looks deliberate
  rather than falling back.
- **Literata** — drawn for screen reading; the sturdiest of the three at
  11–16 sp.

Only one face is applied at a time, and the platform font stays the default.
Verification codes ignore all of them and stay monospace.
