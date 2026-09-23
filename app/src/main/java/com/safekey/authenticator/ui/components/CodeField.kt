package com.safekey.authenticator.ui.components

import com.safekey.authenticator.network.LanSession

/**
 * Caret-safe state machine for digit-only code fields (the LAN pairing code).
 *
 * Why this exists: the `String` overload of the text fields keeps its own
 * `TextFieldValue` internally and maps the caret by diffing the text the
 * input method produced against the text the caller feeds back. This screen
 * reformats that text (digits only + 4-4-4 grouping), so the mapping can
 * land the caret at index 0 or just before the digit that was just typed —
 * the next digit is then inserted in front of the ones already there and the
 * code comes out in the wrong order. Holding a `TextFieldValue` and
 * computing the caret ourselves makes the position deterministic on every
 * keyboard/IME (Chinese IMEs included, which is where it was reported).
 *
 * [resolve] returns the text to show plus the caret index inside it, derived
 * from how many digits the user has actually entered — never from the raw
 * index the framework guessed.
 */
object CodeField {

    /** Display text plus the caret index (inside [text]) after one edit pass. */
    data class Resolved(val text: String, val caret: Int)

    /**
     * Maps one edit of the field to the state the caller must feed back.
     *
     * @param previous previous display text of the field (grouped).
     * @param previousCaret caret index [previous] carried, i.e. where the user
     *   was typing before this edit.
     * @param incoming text the input method just produced.
     * @param caret caret index inside [incoming].
     * @param maxDigits how many digits the code holds.
     */
    fun resolve(
        previous: String,
        previousCaret: Int,
        incoming: String,
        caret: Int,
        maxDigits: Int = LanSession.PAIRING_CODE_DIGITS
    ): Resolved {
        val previousDigits = digits(previous, maxDigits)
        var digits = digits(incoming, Int.MAX_VALUE)
        var caretDigits = countDigitsBefore(incoming, caret)

        // Front-insert repair: digits that end with exactly what was already
        // there, plus one new digit in front ("1234 5678" → incoming
        // "91234 5678"), mean the framework handed the caret to the IME at
        // index 0. Only when the user was actually typing at the end (our own
        // caret sat on the last character) is that a glitch rather than a
        // deliberate tap in front of the first digit — the code is read and
        // typed left to right, so treat it as an append instead of silently
        // reversing the order.
        val wasTypingAtTheEnd = previous.isEmpty() || previousCaret >= previous.length
        if (previousDigits.isNotEmpty() && wasTypingAtTheEnd &&
            digits.length == previousDigits.length + 1 &&
            digits.endsWith(previousDigits)
        ) {
            digits = previousDigits + digits.first()
            caretDigits = digits.length
        }

        val kept = digits.take(maxDigits)
        val grouped = LanSession.groupedCode(kept)
        return Resolved(grouped, offsetForDigits(grouped, caretDigits.coerceAtMost(kept.length)))
    }

    /** Digits of [text] only, at most [maxDigits] of them. */
    fun digits(text: String, maxDigits: Int = LanSession.PAIRING_CODE_DIGITS): String =
        text.filter { it.isDigit() }.take(maxDigits)

    /** How many digits sit before [caret] inside [text]. */
    fun countDigitsBefore(text: String, caret: Int): Int =
        text.take(caret.coerceIn(0, text.length)).count { it.isDigit() }

    /**
     * Display index right after the [digitIndex]-th digit of [grouped] —
     * i.e. where the caret belongs so that the next digit lands where the
     * user expects, never before a group separator.
     */
    fun offsetForDigits(grouped: String, digitIndex: Int): Int {
        if (digitIndex <= 0) return 0
        var seen = 0
        for (index in grouped.indices) {
            if (grouped[index].isDigit()) {
                seen++
                if (seen == digitIndex) return index + 1
            }
        }
        return grouped.length
    }
}
