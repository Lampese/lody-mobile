package app.lody.android.create

import app.lody.android.runtime.CatalogProject
import app.lody.android.runtime.CatalogSession
import app.lody.android.runtime.CatalogSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateSessionModelTest {
    private val snapshot = CatalogSnapshot(
        revision = 1,
        projects = listOf(
            CatalogProject("m1:local:a", "m1", "alpha", "/a"),
            CatalogProject("github:lody/web", "", "lody/web", ""),
            CatalogProject("m1:unassigned", "m1", "", ""),
        ),
        sessions = listOf(
            CatalogSession("s1", "m1", "github:lody/web", "t", "idle", false, false, "2026", lastMessageAt = 20),
            CatalogSession("s2", "m1", "m1:local:a", "t", "idle", false, false, "2026", lastMessageAt = 10),
        ),
        machineIds = listOf("m1", "m2"),
        machineNames = mapOf("m1" to "MacBook"),
    )

    @Test
    fun projectsSkipChatsAndPutTheMostRecentFirst() {
        assertEquals(listOf("github:lody/web", "m1:local:a"), CreateSessionModel.projects(snapshot).map { it.id })
        assertEquals(listOf("m1" to "MacBook", "m2" to "m2"), CreateSessionModel.machines(snapshot))
    }

    @Test
    fun localProjectsFixTheirComputerAndOnlyLocalOnesKeepWorktree() {
        val github = CreateSessionModel.initial(snapshot)
        assertEquals("github:lody/web", github.projectId)
        assertTrue(CreateSessionModel.machineChoosable(snapshot, github))
        val local = CreateSessionModel.select(snapshot, github.copy(machineId = "m2"), "m1:local:a").copy(worktree = true)
        assertEquals("m1", local.machineId)
        assertFalse(CreateSessionModel.machineChoosable(snapshot, local))
        assertFalse(CreateSessionModel.select(snapshot, local, "github:lody/web").worktree)
        val chat = CreateSessionModel.chat(snapshot, local)
        assertTrue(chat.chat)
        assertEquals("m1", chat.machineId)
    }
}
