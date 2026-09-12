# Legal document sources

Per-language source files for the documents published on the website:

- `terms/<code>.md` — Terms of Use (user agreement)
- `privacy/<code>.md` — Privacy Policy
- `<code>` ∈ `en`, `zh-Hans`, `zh-Hant`, `de`, `es`, `fr`, `hi`, `ja`, `ko`, `ru`

The pages at <https://osmium.im/useragreement/> and <https://osmium.im/privacypolicy/>
are generated from these files, and the app fetches the plain-text versions
(`<code>.txt` next to each page) from the same URLs every time it opens.
