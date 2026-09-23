package com.safekey.authenticator.update

/**
 * Turns a GitHub release body into something readable inside a plain-Text
 * dialog.
 *
 * Release notes are Markdown; the update dialog has no Markdown renderer (and
 * pulling one in for a changelog is not worth the dependencies), so the syntax
 * that would otherwise show up literally — `#` headings, `**bold**`,
 * `[text](url)` links, `>` quotes, CRLF line endings — is stripped. The notes
 * stay verbatim otherwise: nothing is summarized and no section is dropped.
 */
object ReleaseNotes {

    /** Guard against a pathological release body blowing up the dialog. */
    private const val MAX_CHARS = 4_000

    private val LINK = Regex("\\[([^\\]]*)\\]\\(([^)]*)\\)")
    private val HEADING = Regex("(?m)^\\s{0,3}#{1,6}\\s*")
    private val QUOTE = Regex("(?m)^\\s{0,3}>\\s?")
    private val BULLET = Regex("(?m)^\\s{0,3}[-*+]\\s+")
    private val BLANK_RUN = Regex("\n{3,}")

    fun clean(raw: String): String {
        if (raw.isBlank()) return ""
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = LINK.replace(text, "$1")
        text = HEADING.replace(text, "")
        text = QUOTE.replace(text, "")
        text = BULLET.replace(text, "• ")
        text = text.replace("**", "").replace("__", "").replace("`", "")
        text = BLANK_RUN.replace(text, "\n\n")
        text = text.trim()
        if (text.length <= MAX_CHARS) return text
        return text.take(MAX_CHARS).trimEnd() + " …"
    }
}
