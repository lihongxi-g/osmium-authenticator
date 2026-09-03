package com.safekey.authenticator.data

import org.junit.Assert.assertTrue
import org.junit.Test

class AppSettingsTest {

    @Test
    fun `tags are enabled by default for existing users`() {
        assertTrue(AppSettings().tagsEnabled)
    }
}
