package com.safekey.authenticator.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * JVM tests for the OTP-field heuristics. InputType class values mirror
 * android.text.InputType (TYPE_CLASS_TEXT = 0x1, TYPE_CLASS_NUMBER = 0x2).
 */
class OtpFieldDetectorTest {

    private val text = 0x00000001
    private val number = 0x00000002

    private fun field(
        id: String? = null,
        hint: String? = null,
        hints: List<String> = emptyList(),
        inputType: Int? = number,
        focused: Boolean = false,
        visible: Boolean = true,
        autofillable: Boolean = true,
    ) = FieldInfo(
        idEntry = id,
        hintText = hint,
        autofillHints = hints,
        rawInputType = inputType,
        isFocused = focused,
        isVisible = visible,
        autofillable = autofillable,
    )

    @Test
    fun `official 2fa app hint is recognized above everything`() {
        val fields = listOf(
            field(id = "other"),
            field(hints = listOf("2faAppOTPCode")),
        )
        val d = OtpFieldDetector.detect(fields)
        assertNotNull(d)
        assertEquals(1, d!!.index)
        assertEquals(DetectionLevel.HINT_2FA_APP, d.level)
    }

    @Test
    fun `sms otp hint is recognized`() {
        val d = OtpFieldDetector.detect(listOf(field(hints = listOf("smsOTPCode"))))
        assertNotNull(d)
        assertEquals(DetectionLevel.HINT_SMS, d!!.level)
    }

    @Test
    fun `per-digit sms otp hints prefer the first position`() {
        val fields = listOf(
            field(hints = listOf("smsOTPCode3")),
            field(hints = listOf("smsOTPCode2")),
            field(hints = listOf("smsOTPCode1")),
        )
        val d = OtpFieldDetector.detect(fields)
        assertNotNull(d)
        assertEquals(2, d!!.index) // "smsOTPCode1"
    }

    @Test
    fun `verification-code id with numeric field is a context match`() {
        val d = OtpFieldDetector.detect(listOf(field(id = "login_verify_code")))
        assertNotNull(d)
        assertEquals(DetectionLevel.CONTEXT, d!!.level)
    }

    @Test
    fun `strong wording is accepted even in a text field (steam-style codes)`() {
        val d = OtpFieldDetector.detect(listOf(field(id = "otp", inputType = text)))
        assertNotNull(d)
        assertEquals(DetectionLevel.CONTEXT, d!!.level)
    }

    @Test
    fun `chinese hint is recognized`() {
        val d = OtpFieldDetector.detect(listOf(field(hint = "请输入验证码")))
        assertNotNull(d)
    }

    @Test
    fun `chinese strong wording works without numeric type`() {
        val d = OtpFieldDetector.detect(listOf(field(id = "动态码", inputType = text)))
        assertNotNull(d)
        assertEquals(DetectionLevel.CONTEXT, d!!.level)
    }

    @Test
    fun `zip code field is rejected`() {
        assertNull(OtpFieldDetector.detect(listOf(field(id = "zip_code"))))
    }

    @Test
    fun `postal code hint text is rejected`() {
        assertNull(OtpFieldDetector.detect(listOf(field(hint = "Postal code"))))
    }

    @Test
    fun `official promo code hint is rejected`() {
        assertNull(
            OtpFieldDetector.detect(
                listOf(field(id = "promo_code", hints = listOf("promoCode")))
            )
        )
    }

    @Test
    fun `captcha field is rejected`() {
        assertNull(OtpFieldDetector.detect(listOf(field(id = "captcha_input"))))
    }

    @Test
    fun `invisible fields are skipped`() {
        assertNull(
            OtpFieldDetector.detect(listOf(field(hints = listOf("smsOTPCode"), visible = false)))
        )
    }

    @Test
    fun `non-autofillable fields are skipped`() {
        assertNull(
            OtpFieldDetector.detect(listOf(field(hints = listOf("smsOTPCode"), autofillable = false)))
        )
    }

    @Test
    fun `focused field wins a weak tie`() {
        val fields = listOf(
            field(id = "code"),
            field(id = "code_confirm", focused = true),
        )
        val d = OtpFieldDetector.detect(fields)
        assertNotNull(d)
        assertEquals(1, d!!.index)
        assertEquals(DetectionLevel.WEAK, d.level)
    }

    @Test
    fun `weak wording needs a numeric field`() {
        // "code"/"token" alone are only trusted when the field takes digits.
        assertNull(OtpFieldDetector.detect(listOf(field(id = "case_code", inputType = text))))
        assertNull(OtpFieldDetector.detect(listOf(field(id = "some_token", inputType = text))))
    }

    @Test
    fun `underscore ids match spaced keywords`() {
        val d = OtpFieldDetector.detect(listOf(field(id = "sms_code")))
        assertNotNull(d)
        assertEquals(DetectionLevel.CONTEXT, d!!.level)
    }

    @Test
    fun `sms hint beats a plain code field`() {
        val fields = listOf(
            field(id = "code"),
            field(hints = listOf("smsOTPCode")),
        )
        val d = OtpFieldDetector.detect(fields)
        assertNotNull(d)
        assertEquals(1, d!!.index)
        assertEquals(DetectionLevel.HINT_SMS, d.level)
    }

    @Test
    fun `null input type with strong wording still matches`() {
        val d = OtpFieldDetector.detect(listOf(field(id = "totp_code", inputType = null)))
        assertNotNull(d)
    }
}
