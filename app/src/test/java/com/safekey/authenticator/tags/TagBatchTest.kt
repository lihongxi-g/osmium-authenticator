package com.safekey.authenticator.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagBatchTest {

    @Test
    fun `refs pairs every selected account with every selected tag`() {
        val refs = TagBatch.refs(setOf("b", "a"), setOf("two", "one"))
        assertEquals(
            listOf(
                "a" to "one", "a" to "two",
                "b" to "one", "b" to "two"
            ),
            refs
        )
    }

    @Test
    fun `refs is empty unless both sides have a selection`() {
        assertTrue(TagBatch.refs(emptySet(), setOf("one")).isEmpty())
        assertTrue(TagBatch.refs(setOf("a"), emptySet()).isEmpty())
        assertFalse(TagBatch.canApply(emptySet(), emptySet()))
        assertTrue(TagBatch.canApply(setOf("a"), setOf("one")))
    }

    @Test
    fun `toggle adds then removes an account`() {
        val once = TagBatch.toggle(emptySet(), "a")
        assertEquals(setOf("a"), once)
        assertEquals(emptySet<String>(), TagBatch.toggle(once, "a"))
    }

    @Test
    fun `toggleAll selects everything then clears`() {
        val all = setOf("a", "b")
        assertEquals(all, TagBatch.toggleAll(emptySet(), all))
        assertEquals(emptySet<String>(), TagBatch.toggleAll(all, all))
        // A partial selection completes rather than clearing.
        assertEquals(all, TagBatch.toggleAll(setOf("a"), all))
    }

    @Test
    fun `retainExisting drops ids that are gone`() {
        assertEquals(setOf("a"), TagBatch.retainExisting(setOf("a", "deleted"), setOf("a", "b")))
        assertEquals(emptySet<String>(), TagBatch.retainExisting(setOf("a"), emptyList()))
    }
}
