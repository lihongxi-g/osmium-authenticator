package com.safekey.authenticator.tags

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Test

class TagFilterTest {

    private fun account(id: String, issuer: String, label: String, tagIds: Set<String>) = Account(
        id = id,
        issuer = issuer,
        label = label,
        secret = "JBSWY3DPEHPK3PXP",
        algorithm = Account.ALGO_SHA1,
        digits = 6,
        period = 30,
        sortOrder = 0,
        createdAt = 0,
        updatedAt = 0,
        tags = tagIds.map { tag(it, it) }
    )

    private fun tag(id: String, name: String) = Tag(
        id = id,
        name = name,
        color = TagColor.BLUE,
        createdAt = 0,
        updatedAt = 0
    )

    private val work = tag("work", "Work")
    private val games = tag("games", "Games")
    private val accounts = listOf(
        account("one", "Google", "school@example.com", setOf("work")),
        account("two", "Steam", "player", setOf("games")),
        account("three", "GitHub", "octocat", emptySet()),
        account("four", "Google", "both@example.com", setOf("work", "games"))
    )

    @Test
    fun `all filter returns every account`() {
        assertEquals(listOf("one", "two", "three", "four"), TagFilter.apply(accounts, "", emptySet(), false).map { it.id })
    }

    @Test
    fun `uncategorized filter returns only accounts without tags`() {
        assertEquals(listOf("three"), TagFilter.apply(accounts, "", emptySet(), true).map { it.id })
    }

    @Test
    fun `multiple selected tags use OR semantics`() {
        assertEquals(listOf("one", "two", "four"), TagFilter.apply(accounts, "", setOf(work.id, games.id), false).map { it.id })
    }

    @Test
    fun `search intersects selected tag filters`() {
        assertEquals(listOf("one", "four"), TagFilter.apply(accounts, "google", setOf(work.id), false).map { it.id })
    }

    @Test
    fun `unknown selected tag yields no tagged accounts`() {
        assertEquals(emptyList<String>(), TagFilter.apply(accounts, "", setOf("missing"), false).map { it.id })
    }
}
