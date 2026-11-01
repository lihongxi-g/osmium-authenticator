# Third-party fonts

## Lora

- **Files:** `app/src/main/res/font/lora.ttf` (variable, weights 400–700)
- **Origin:** <https://github.com/cyrealtype/Lora-Cyrillic>, obtained from the
  Google Fonts repository (`ofl/lora`)
- **Copyright:** Copyright 2011 The Lora Project Authors
  (<https://github.com/cyrealtype/Lora-Cyrillic>), with Reserved Font Name
  "Lora".
- **License:** SIL Open Font License, Version 1.1 — full text in
  [`app/src/main/assets/licenses/ofl_lora.txt`](app/src/main/assets/licenses/ofl_lora.txt)
  and shipped inside the APK, together with the upstream `FONTLOG.txt`.
- **Modifications:** none. The file is redistributed byte-for-byte as published
  by the project, with only the file name changed (`Lora[wght].ttf` →
  `lora.ttf`, because Android resource names must be lowercase and may not
  contain brackets). The font is therefore an unmodified copy and keeps the
  reserved name.

The license is also shown in-app: Settings → About → Attributions → *Bundled
libraries* → "Lora (SIL OFL 1.1)".

Lora covers the **latin**, **latin-ext**, **cyrillic**, **cyrillic-ext** and
**vietnamese** subsets (778 code points in the shipped file). There is **no**
Greek subset — the only Greek-block characters present (ΓΔΠΩμπ) come from the
math/symbols subsets — and no CJK, Hangul or Devanagari at all, so Chinese,
Japanese, Korean and Hindi render with the system font when Lora is selected.
That is expected behaviour, and the reason the setting is labelled a choice
rather than a replacement.
