package com.safekey.authenticator.repository

import com.safekey.authenticator.model.Account
import com.safekey.authenticator.model.VaultAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportMergerTest {

    private fun account(id: String, issuer: String, label: String) = Account(
        id = id, issuer = issuer, label = label, secret = "JBSWY3DPEHPK3PXP",
        algorithm = "SHA1", digits = 6, period = 30,
        sortOrder = 0, createdAt = 0, updatedAt = 0
    )

    private fun vault(issuer: String, label: String) = VaultAccount(issuer = issuer, label = label)

    @Test
    fun `all new accounts are added`() {
        val plan = ImportMerger.plan(
            listOf(account("1", "Google", "a@gmail.com")),
            listOf(vault("GitHub", "octocat"), vault("Twitter", "user"))
        )
        assertEquals(2, plan.toAdd.size)
        assertEquals(0, plan.duplicatesCount)
        assertTrue(plan.toUpdate.isEmpty())
    }

    @Test
    fun `matching issuer and label updates`() {
        val existing = listOf(account("1", "Google", "a@gmail.com"))
        val plan = ImportMerger.plan(existing, listOf(vault("Google", "a@gmail.com")))
        assertEquals(0, plan.toAdd.size)
        assertEquals(1, plan.toUpdate.size)
        assertEquals(1, plan.duplicatesCount)
    }

    @Test
    fun `case insensitive matching`() {
        val existing = listOf(account("1", "Google", "A@GMAIL.COM"))
        val plan = ImportMerger.plan(existing, listOf(vault("google", "a@gmail.com")))
        assertEquals(1, plan.duplicatesCount)
        assertTrue(plan.toAdd.isEmpty())
    }

    @Test
    fun `same issuer different label is a new account`() {
        val existing = listOf(account("1", "Google", "a@gmail.com"))
        val plan = ImportMerger.plan(existing, listOf(vault("Google", "b@gmail.com")))
        assertEquals(1, plan.toAdd.size)
        assertEquals(0, plan.duplicatesCount)
    }

    @Test
    fun `empty import into empty db`() {
        val plan = ImportMerger.plan(emptyList(), emptyList())
        assertEquals(0, plan.total)
    }

    @Test
    fun `mixed import`() {
        val existing = listOf(account("1", "Google", "a@gmail.com"))
        val incoming = listOf(
            vault("Google", "a@gmail.com"), // update
            vault("Google", "b@gmail.com"), // new
            vault("GitHub", "octocat") // new
        )
        val plan = ImportMerger.plan(existing, incoming)
        assertEquals(2, plan.toAdd.size)
        assertEquals(1, plan.toUpdate.size)
        assertEquals(3, plan.total)
        assertEquals(1, plan.duplicatesCount)
    }
}
