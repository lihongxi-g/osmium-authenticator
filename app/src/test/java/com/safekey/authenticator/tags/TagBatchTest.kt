package com.safekey.authenticator.tags

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagBatchTest {

    @Test
    fun `refs links each ticked account to the tag`() {
        assertEquals(
            listOf("a" to "work", "b" to "work"),
            TagBatch.refs("work", setOf("b", "a"))
        )
    }

    @Test
    fun `refs is empty without a tag or without accounts`() {
        assertTrue(TagBatch.refs("", setOf("a")).isEmpty())
        assertTrue(TagBatch.refs("work", emptySet()).isEmpty())
        assertFalse(TagBatch.refs("work", setOf("a")).isEmpty())
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
