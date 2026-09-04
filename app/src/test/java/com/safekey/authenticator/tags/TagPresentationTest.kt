package com.safekey.authenticator.tags

import com.safekey.authenticator.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagPresentationTest {

    private val work = Tag("work", "Work", TagColor.BLUE, 0L, 0L)

    @Test
    fun `disabled tag feature hides all tag presentation`() {
        assertEquals(emptyList<Tag>(), TagPresentation.visibleTags(false, listOf(work)))
        assertFalse(TagPresentation.shouldShowFilterRow(false, listOf(work)))
        assertFalse(TagPresentation.shouldShowUncategorized(false, listOf(work)))
    }

    @Test
    fun `uncategorized is hidden when no tags have been created`() {
        assertTrue(TagPresentation.visibleTags(true, emptyList()).isEmpty())
        assertFalse(TagPresentation.shouldShowFilterRow(true, emptyList()))
        assertFalse(TagPresentation.shouldShowUncategorized(true, emptyList()))
    }

    @Test
    fun `enabled feature shows filters only after a tag exists`() {
        assertEquals(listOf(work), TagPresentation.visibleTags(true, listOf(work)))
        assertTrue(TagPresentation.shouldShowFilterRow(true, listOf(work)))
        assertTrue(TagPresentation.shouldShowUncategorized(true, listOf(work)))
    }
}
