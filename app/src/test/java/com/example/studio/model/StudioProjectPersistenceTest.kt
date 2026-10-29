package com.example.studio.model

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.studio.director.ChatTurn
import com.example.studio.director.DirectorBrief
import com.example.studio.director.DirectorStore
import com.example.studio.director.DirectorUi
import com.example.studio.director.DirectorWorkflow
import com.example.ui.components.VideoProjectData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class StudioProjectPersistenceTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun textItem(id: String, text: String) = TextItem(
        id = id, laneId = "lane_text_1", startUs = 0, durationUs = 2_000_000, text = text,
        transform = ItemTransform(x = 12f, y = -7f, scale = 1.4f, rotation = 15f, opacity = 0.5f, blendMode = "Screen"),
        effects = ItemEffects(neonGlow = 0.6f, vignette = 0.3f),
        animIn = ItemAnimation("pop", 250_000L, "easeOutBack"),
        keyframes = listOf(TransformKeyframe(500_000L, x = 3f, scale = 2f))
    )

    @Test
    fun `two projects with the same name stay isolated after edit and reopen`() {
        val storage = StudioProjectStorage(context)
        val a = StudioProject.createEmpty("My Viral Video")
        val b = StudioProject.createEmpty("My Viral Video")
        assertNotEquals(a.id, b.id)

        storage.saveProject(a)
        storage.saveProject(b)
        storage.saveProject(a.copy(items = listOf(textItem("t1", "only in A"))))

        val reA = StudioProjectStorage(context).loadProject(a.id)
        val reB = StudioProjectStorage(context).loadProject(b.id)
        assertNotNull(reA); assertNotNull(reB)
        assertEquals(1, reA!!.items.size)
        assertEquals(0, reB!!.items.size)
        assertEquals(2, storage.listProjects().count { it.name == "My Viral Video" })
    }

    @Test
    fun `item transform effects animations and keyframes survive a reload`() {
        val storage = StudioProjectStorage(context)
        val p = StudioProject.createEmpty("Round trip").copy(items = listOf(textItem("t1", "hello")))
        storage.saveProject(p)
        val item = storage.loadProject(p.id)!!.items.single() as TextItem
        assertEquals("hello", item.text)
        assertEquals(1.4f, item.transform.scale, 0.001f)
        assertEquals("Screen", item.transform.blendMode)
        assertEquals(0.6f, item.effects.neonGlow, 0.001f)
        assertEquals("pop", item.animIn?.type)
        assertEquals(1, item.keyframes.size)
        assertEquals(500_000L, item.keyframes[0].timeUs)
    }

    @Test
    fun `saved studio project wins over legacy migration`() {
        val legacy = VideoProjectData(projectName = "Reopen me")
        val first = StudioProjectResolver.resolve(context, legacy)
        assertEquals(legacy.projectId, first.id)

        StudioProjectStorage(context).saveProject(first.copy(items = listOf(textItem("ai1", "AI work"))))
        val reopened = StudioProjectResolver.resolve(context, legacy)
        assertEquals(1, reopened.items.size)
        assertEquals("AI work", (reopened.items[0] as TextItem).text)
    }

    @Test
    fun `director chat is isolated per project id`() {
        val store = DirectorStore(context)
        val wf = DirectorWorkflow.ZERO_TO_VIDEO
        val idA = ProjectIdentity.newId()
        val idB = ProjectIdentity.newId()
        store.save(idA, DirectorUi(wf, DirectorBrief(wf), turns = listOf(ChatTurn(fromUser = true, text = "topic for A"))))

        assertNull(store.load(idB, wf))
        assertEquals("topic for A", store.load(idA, wf)!!.turns.single().text)
    }

    @Test
    fun `legacy name-hash data moves to the permanent id`() {
        val name = "Old Project"
        val oldId = ProjectIdentity.legacyHashId(name)
        val newId = ProjectIdentity.newId()
        val storage = StudioProjectStorage(context)
        storage.saveProject(StudioProject.createEmpty(name).copy(id = oldId, items = listOf(textItem("t", "kept"))))
        val store = DirectorStore(context)
        val wf = DirectorWorkflow.ZERO_TO_VIDEO
        store.save(oldId, DirectorUi(wf, DirectorBrief(wf), turns = listOf(ChatTurn(fromUser = true, text = "old chat"))))

        ProjectIdentity.adoptLegacyIdentity(context, name, newId)

        assertEquals("kept", (storage.loadProject(newId)!!.items.single() as TextItem).text)
        assertFalse(storage.exists(oldId))
        assertEquals("old chat", store.load(newId, wf)!!.turns.single().text)
        assertTrue(store.load(oldId, wf) == null)
    }
}
