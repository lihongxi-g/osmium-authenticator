package com.safekey.authenticator.ui.components

import com.safekey.authenticator.network.LanSession
import org.junit.Assert.assertEquals
import org.junit.Test

class CodeFieldTest {

    /** One edit: [incoming] is what the IME produced for the caret in [previous]. */
    private fun edit(
        previous: CodeField.Resolved,
        incoming: String,
        caret: Int = incoming.length
    ): CodeField.Resolved = CodeField.resolve(previous.text, previous.caret, incoming, caret)

    @Test
    fun groupingMatchesTheLanSessionFormat() {
        assertEquals("1234 5678 9012", LanSession.groupedCode("123456789012"))
        assertEquals("1234 5678 9", LanSession.groupedCode("123456789"))
        assertEquals("123", LanSession.groupedCode("123"))
        assertEquals("", LanSession.groupedCode(""))
    }

    @Test
    fun offsetForDigits_sitsAfterTheDigit() {
        val twelve = LanSession.groupedCode("123456789012")
        assertEquals(0, CodeField.offsetForDigits(twelve, 0))
        assertEquals(1, CodeField.offsetForDigits(twelve, 1))
        assertEquals(4, CodeField.offsetForDigits(twelve, 4))
        // 5th digit lives at index 5 (index 4 is the separator).
        assertEquals(6, CodeField.offsetForDigits(twelve, 5))
        // 9th digit: two separators in between (indices 4 and 9).
        assertEquals(11, CodeField.offsetForDigits(twelve, 9))
        assertEquals(14, CodeField.offsetForDigits(twelve, 12))
    }

    @Test
    fun typingSequentially_keepsTheOrderAndTheCaretAtTheEnd() {
        // Regression: the 5th digit used to leave the caret *before* it, so the
        // next digit was inserted in front of the previous one.
        var state = edit(CodeField.Resolved("", 0), "1")
        assertEquals("1", state.text)
        assertEquals(1, state.caret)

        state = edit(state, "12")
        assertEquals("12", state.text)
        assertEquals(2, state.caret)

        state = edit(CodeField.Resolved("1234", 4), "12345")
        assertEquals("1234 5", state.text)
        assertEquals(6, state.caret)

        state = edit(state, "1234 56")
        assertEquals("1234 56", state.text)
        assertEquals(7, state.caret)

        state = edit(state, "1234 5678")
        assertEquals("1234 5678", state.text)
        assertEquals(9, state.caret)

        state = edit(state, "1234 56789")
        assertEquals("1234 5678 9", state.text)
        assertEquals(11, state.caret)

        state = edit(state, "1234 5678 90")
        assertEquals("1234 5678 90", state.text)
        assertEquals(12, state.caret)
    }

    @Test
    fun frontInsertedDigit_isRepairedToAnAppend() {
        // What a caret stuck at index 0 produces while typing at the end: the
        // new digit arrives first in the text.
        val state = edit(CodeField.Resolved("1234 5678", 9), "91234 5678", 1)
        assertEquals("1234 5678 9", state.text)
        assertEquals(11, state.caret)
    }

    @Test
    fun frontInsertRepair_needsTheSameDigitsInOrder() {
        // Not a front insert (the digits do not line up) → keep what arrived.
        val state = edit(CodeField.Resolved("1234 5678", 9), "91234 5670", 1)
        assertEquals("9123 4567 0", state.text)
        assertEquals(1, state.caret)
    }

    @Test
    fun deliberateTapInFrontOfTheFirstDigit_isNotRepaired() {
        // The user tapped before the first digit (our caret was at index 0) and
        // typed: that is an intended insert, not the framework glitch.
        val state = edit(CodeField.Resolved("1234 5678", 0), "91234 5678", 1)
        assertEquals("9123 4567 8", state.text)
        assertEquals(1, state.caret)
    }

    @Test
    fun midTextEdit_keepsTheCaretAfterTheInsertedDigit() {
        // Tapped right after the 4th digit and typed 9: the digit stays where
        // it was typed, the caret follows it.
        val state = edit(CodeField.Resolved("1234 5678", 9), "12349 5678", 5)
        assertEquals("1234 9567 8", state.text)
        assertEquals(6, state.caret)
    }

    @Test
    fun deletingADigit_keepsTheCaretInPlace() {
        val state = edit(CodeField.Resolved("1234 5678", 9), "1234 678")
        assertEquals("1234 678", state.text)
        assertEquals(8, state.caret)
    }

    @Test
    fun pastingMoreThanTheCodeLength_keepsTheFirstDigitsOnly() {
        val state = edit(CodeField.Resolved("", 0), "123456789012345")
        assertEquals("1234 5678 9012", state.text)
        assertEquals(14, state.caret)
    }

    @Test
    fun typingPastTheCodeLength_doesNotGrowTheField() {
        val full = "1234 5678 9012"
        val state = edit(CodeField.Resolved(full, full.length), full + "3")
        assertEquals(full, state.text)
        assertEquals(full.length, state.caret)
    }

    @Test
    fun separatorsTypedByHand_areDropped() {
        val state = edit(CodeField.Resolved("1234", 4), "1234 5 6", 8)
        assertEquals("1234 56", state.text)
        assertEquals(7, state.caret)
    }

    @Test
    fun clearingTheField_leavesAnEmptyState() {
        val state = edit(CodeField.Resolved("1234", 4), "", 0)
        assertEquals("", state.text)
        assertEquals(0, state.caret)
    }

    @Test
    fun digitsOnlyCapKeepsTheFieldReproducible() {
        assertEquals("123456789012", CodeField.digits("1-2 3_4/5.6(7)8,9 012", 12))
        assertEquals("", CodeField.digits("abc", 12))
        // Caret after "124 5": four digits sit before it.
        assertEquals(4, CodeField.countDigitsBefore("124 56", 5))
    }
}
