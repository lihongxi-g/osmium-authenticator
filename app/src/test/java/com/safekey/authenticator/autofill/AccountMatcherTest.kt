package com.safekey.authenticator.autofill

import org.junit.Assert.assertEquals
import org.junit.Test

class AccountMatcherTest {

    private fun account(id: String, issuer: String, label: String) = AccountLite(id, issuer, label)

    @Test
    fun `binding always wins`() {
        val accounts = listOf(
            account("a", "GitHub", "me@example.com"),
            account("b", "Google", "me@gmail.com"),
        )
        val ranked = AccountMatcher.rank(accounts, boundAccountId = "b", appLabel = "GitHub")
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `exact issuer match ranks first without a binding`() {
        val accounts = listOf(
            account("a", "Google", "me@gmail.com"),
            account("b", "GitHub", "me@example.com"),
        )
        val ranked = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = "GitHub")
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `containment match works in both directions`() {
        val accounts = listOf(
            account("a", "Google", "me@gmail.com"),
            account("b", "Acme", "work"),
        )
        val ranked = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = "Acme Corp")
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `token overlap matches multi-word labels`() {
        val accounts = listOf(
            account("a", "Google", "me@gmail.com"),
            account("b", "Acme", "work"),
        )
        val ranked = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = "Bank Acme")
        assertEquals("b", ranked.first().id)
    }

    @Test
    fun `unmatched accounts keep their original order`() {
        val accounts = listOf(
            account("a", "AAA", "one"),
            account("b", "BBB", "two"),
            account("c", "CCC", "three"),
        )
        val ranked = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = "Unrelated App")
        assertEquals(listOf("a", "b", "c"), ranked.map { it.id })
    }

    @Test
    fun `blank app label keeps original order`() {
        val accounts = listOf(account("a", "AAA", "one"), account("b", "BBB", "two"))
        val ranked = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = null)
        assertEquals(listOf("a", "b"), ranked.map { it.id })
        val ranked2 = AccountMatcher.rank(accounts, boundAccountId = null, appLabel = "")
        assertEquals(listOf("a", "b"), ranked2.map { it.id })
    }
}
