package com.safekey.authenticator.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `tags are enabled by default for existing users`() {
        assertTrue(AppSettings().tagsEnabled)
    }

    @Test
    fun `preview update checks are off by default`() {
        // A preview build must never arrive uninvited: the switch starts off
        // and an existing install has no stored value for it.
        assertFalse(AppSettings().checkPreviewUpdates)
    }
}
