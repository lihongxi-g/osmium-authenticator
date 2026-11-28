package com.safekey.authenticator.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class IconMatcherTest {

    private fun icon(name: String, issuers: List<String>) = PackIcon(
        name = name,
        relativePath = "icons/$name.svg",
        file = File("icons/$name.svg"),
        type = IconType.SVG,
        issuers = issuers,
        category = null
    )

    private fun pack(vararg icons: PackIcon) = InstalledIconPack(
        uuid = "c1018b93-4e8c-490a-b575-30dde62a833e",
        name = "test",
        version = 1,
        directory = File("icons"),
        icons = icons.toList()
    )

    @Test
    fun `alias containing the issuer is a normal match`() {
        val p = pack(icon("google", listOf("google")), icon("github", listOf("github")))
        val result = IconMatcher.suggestions(p, "Google")
        assertEquals(listOf("google"), result.map { it.name })
    }

    @Test
    fun `issuer containing the alias is still suggested, after normal matches`() {
        val p = pack(
            icon("workspace", listOf("workspace")),
            icon("google", listOf("google"))
        )
        val result = IconMatcher.suggestions(p, "Google Workspace")
        assertEquals("google", result.first().name)
        assertEquals(2, result.size)
    }

    @Test
    fun `matching is case insensitive`() {
        val p = pack(icon("google", listOf("GOOGLE")))
        assertEquals(1, IconMatcher.suggestions(p, "google").size)
    }

    @Test
    fun `chinese issuers expand to english aliases`() {
        val p = pack(
            icon("wechat", listOf("wechat")),
            icon("weibo", listOf("weibo")),
            icon("alipay", listOf("alipay"))
        )
        assertEquals(listOf("wechat"), IconMatcher.suggestions(p, "微信").map { it.name })
        assertEquals(listOf("weibo"), IconMatcher.suggestions(p, "微博").map { it.name })
        assertEquals(listOf("alipay"), IconMatcher.suggestions(p, "支付宝").map { it.name })
    }

    @Test
    fun `unknown issuer produces no suggestions`() {
        val p = pack(icon("google", listOf("google")))
        assertTrue(IconMatcher.suggestions(p, "Some Unlisted Service").isEmpty())
        assertNull(IconMatcher.bestMatch(p, "Some Unlisted Service"))
    }

    @Test
    fun `search matches names and aliases`() {
        val p = pack(icon("Google", listOf("google", "gmail")))
        assertEquals(1, IconMatcher.search(p, "gma").size)
        assertEquals(1, IconMatcher.search(p, "goo").size)
        assertEquals(0, IconMatcher.search(p, "zzz").size)
    }

    @Test
    fun `best match returns the first suggestion`() {
        val p = pack(
            icon("workspace", listOf("workspace")),
            icon("google", listOf("google"))
        )
        assertEquals("google", IconMatcher.bestMatch(p, "Google Workspace")?.name)
    }
}
