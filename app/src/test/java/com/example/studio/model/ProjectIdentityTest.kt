package com.example.studio.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectIdentityTest {

    @Test
    fun `ids are unique uuids and never derived from the name`() {
        val a = ProjectIdentity.newId()
        val b = ProjectIdentity.newId()
        assertNotEquals(a, b)
        assertEquals(36, a.length)
        assertTrue(!a.startsWith("proj_"))
    }

    @Test
    fun `untitled names count up and skip used numbers`() {
        assertEquals("Untitled 1", ProjectIdentity.nextUntitled(emptyList()))
        assertEquals("Untitled 3", ProjectIdentity.nextUntitled(listOf("Untitled 1", "untitled 2")))
        assertEquals("Untitled 2", ProjectIdentity.nextUntitled(listOf("Untitled 1", "My Video")))
    }

    @Test
    fun `unique name appends a counter only when needed`() {
        assertEquals("My Video", ProjectIdentity.uniqueName("My Video", listOf("Other")))
        assertEquals("My Video 2", ProjectIdentity.uniqueName("My Video", listOf("my video")))
        assertEquals("My Video 3", ProjectIdentity.uniqueName("My Video", listOf("My Video", "My Video 2")))
        assertEquals("Untitled 1", ProjectIdentity.uniqueName("   ", emptyList()))
    }

    @Test
    fun `legacy hash id is stable for the same name`() {
        assertEquals(ProjectIdentity.legacyHashId("My Viral Video"), ProjectIdentity.legacyHashId("My Viral Video"))
        assertTrue(ProjectIdentity.legacyHashId("x").startsWith("proj_"))
    }
}
