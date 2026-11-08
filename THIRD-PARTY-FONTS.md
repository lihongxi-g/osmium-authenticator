# Third-party fonts

Osmium bundles one optional interface face, **Literata**, licensed under the
**SIL Open Font License, Version 1.1**, which permits bundling and
redistribution inside an application as long as the copyright notice and the
licence travel with the font.

The files are redistributed **byte-for-byte as published**, with only the file
name lowercased (`Literata[opsz,wght].ttf` → `literata.ttf`), because Android
resource names must be lowercase `[a-z0-9_]`. Renaming the file is not a
modification of the font software, so the Reserved Font Name stays untouched.

| App label | Files (`app/src/main/res/font/`) | Sizes | Scripts |
|---|---|---|---|
| Literata | `literata` (400), `literata_medium` (500), `literata_semibold` (600), `literata_bold` (700), `literata_extrabold` (800), `literata_black` (900) | 933 / 525 / 525 / 524 / 523 / 503 KB | latin, latin-ext, cyrillic, cyrillic-ext, greek, greek-ext, vietnamese |

Measured from the shipped files: Literata covers 1163 code points. **It has no
CJK, Hangul or Devanagari**, so Chinese, Japanese, Korean and Hindi text renders
with the system font. A Chinese face was bundled in an earlier revision and
dropped again: its subset files alone weighed 12 MB, and the app explains the
gap on the font page instead (with a shortcut to the language setting).

## Static instances

The face ships fixed weight files, not the published variable font. The
instances were generated with `fontTools.varLib.instancer` at the weights the
app declares. Weights above the heaviest file are synthesised by the platform.

## Copyright and licence

- **Literata** — Copyright 2017 The Literata Project Authors
  (<https://github.com/googlefonts/literata>), with Reserved Font Name
  "Literata". Source: Google Fonts `ofl/literata`.

Full licence text ships inside the APK:
`app/src/main/assets/licenses/ofl_literata.txt`.

The attribution is also visible in-app: Settings → About → Attributions →
*Bundled libraries*, with a link to the upstream project.

## Why this one

- **Literata** — drawn for screen reading; the sturdiest Latin face at 11–16 sp.

The platform font stays the default. Verification codes ignore the bundled face
and stay monospace.

## Removed faces

Lora was bundled up to 2.6.4.1 and dropped in favour of Literata alone: it
covered fewer code points (778 against 1163), and two serif options on the same
page were a choice with no meaningful difference. A stored `lora` preference
from an older install resolves to Literata, so those users keep a serif
interface instead of falling back to the platform font.
