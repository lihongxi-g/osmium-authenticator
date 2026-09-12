package com.safekey.authenticator.autofill

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutofillBindingsTest {

    @Test
    fun `round trip preserves entries`() {
        val bindings = mapOf(
            "com.example.app" to "account-1",
            "com.other.app" to "account-2",
        )
        assertEquals(bindings, AutofillBindings.decode(AutofillBindings.encode(bindings)))
    }

    @Test
    fun `empty map round trips`() {
        assertTrue(AutofillBindings.decode(AutofillBindings.encode(emptyMap())).isEmpty())
    }

    @Test
    fun `null and blank input decode to empty`() {
        assertTrue(AutofillBindings.decode(null).isEmpty())
        assertTrue(AutofillBindings.decode("").isEmpty())
        assertTrue(AutofillBindings.decode("   ").isEmpty())
    }

    @Test
    fun `corrupt json decodes to empty instead of crashing`() {
        assertTrue(AutofillBindings.decode("{not valid json").isEmpty())
        assertTrue(AutofillBindings.decode("[1,2,3]").isEmpty())
    }
}
