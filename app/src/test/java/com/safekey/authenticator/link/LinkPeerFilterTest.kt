package com.safekey.authenticator.link

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkPeerFilterTest {
    @Test fun `own service is hidden`() {
        assertFalse(LinkPeerFilter.shouldShow("Osmium-Pixel-abc", "Osmium-Pixel-abc"))
    }

    @Test fun `other service is shown`() {
        assertTrue(LinkPeerFilter.shouldShow("Osmium-Pixel-abc", "Osmium-Other-def"))
    }

    @Test fun `blank service is hidden`() {
        assertFalse(LinkPeerFilter.shouldShow("", "Osmium-Other-def"))
    }
}
