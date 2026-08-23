package com.safekey.authenticator.repository

import com.safekey.authenticator.model.Tag
import com.safekey.authenticator.model.VaultTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TagImportPlannerTest {

    private fun tag(id: String, name: String) = Tag(id, name, "blue", 0, 0)

    private fun vt(id: String, name: String, color: String = "blue") = VaultTag(id, name, color)

    // ---- new tags --------------------------------------------------------

    @Test
    fun `incoming tags without a local match are queued for creation`() {
        val plan = TagImportPlanner.plan(emptyList(), listOf(vt("t1", "Work"), vt("t2", "Personal")))
        assertEquals(listOf("Work", "Personal"), plan.toCreate.map { it.name })
        assertEquals(2, plan.idMap.size)
        // placeholders resolve to the incoming ids until created
        assertEquals("t1", plan.idMap["t1"])
        assertEquals("t2", plan.idMap["t2"])
    }

    // ---- merge with existing tags ----------------------------------------

    @Test
    fun `same name maps to the existing local tag`() {
        val plan = TagImportPlanner.plan(listOf(tag("L1", "Work")), listOf(vt("t1", "Work")))
        assertTrue(plan.toCreate.isEmpty())
        assertEquals("L1", plan.idMap["t1"])
    }

    @Test
    fun `case-insensitive name maps to the existing local tag`() {
        val plan = TagImportPlanner.plan(listOf(tag("L1", "Work")), listOf(vt("t1", "work")))
        assertTrue(plan.toCreate.isEmpty())
        assertEquals("L1", plan.idMap["t1"])
    }

    @Test
    fun `whitespace is ignored when matching`() {
        val plan = TagImportPlanner.plan(listOf(tag("L1", "Work")), listOf(vt("t1", "  Work  ")))
        assertTrue(plan.toCreate.isEmpty())
        assertEquals("L1", plan.idMap["t1"])
    }

    // ---- invalid names are skipped, never thrown --------------------------

    @Test
    fun `blank name is skipped without crashing`() {
        val plan = TagImportPlanner.plan(emptyList(), listOf(vt("t1", "  "), vt("t2", "Work")))
        assertNull(plan.idMap["t1"])
        assertEquals(listOf("Work"), plan.toCreate.map { it.name })
    }

    @Test
    fun `overlong name is skipped without crashing`() {
        val plan = TagImportPlanner.plan(emptyList(), listOf(vt("t1", "a".repeat(21)), vt("t2", "ok")))
        assertNull(plan.idMap["t1"])
        assertEquals(listOf("ok"), plan.toCreate.map { it.name })
    }

    // ---- duplicates inside the backup -------------------------------------

    @Test
    fun `duplicate names inside the backup collapse to the first tag`() {
        val plan = TagImportPlanner.plan(emptyList(), listOf(vt("t1", "Work"), vt("t2", "work")))
        assertEquals(1, plan.toCreate.size)
        assertEquals("Work", plan.toCreate[0].name)
        // both incoming ids resolve to the same placeholder
        assertEquals(plan.idMap["t1"], plan.idMap["t2"])
    }

    @Test
    fun `duplicate of an existing tag never queues creation`() {
        val plan = TagImportPlanner.plan(listOf(tag("L1", "Work")), listOf(vt("t1", "Work"), vt("t2", "WORK")))
        assertTrue(plan.toCreate.isEmpty())
        assertEquals("L1", plan.idMap["t1"])
        assertEquals("L1", plan.idMap["t2"])
    }

    // ---- misc --------------------------------------------------------------

    @Test
    fun `empty inputs produce an empty plan`() {
        val plan = TagImportPlanner.plan(emptyList(), emptyList())
        assertTrue(plan.idMap.isEmpty())
        assertTrue(plan.toCreate.isEmpty())
    }

    @Test
    fun `incoming color is preserved for tags to create`() {
        val plan = TagImportPlanner.plan(emptyList(), listOf(vt("t1", "Important", "red")))
        assertEquals("red", plan.toCreate[0].color)
    }
}
