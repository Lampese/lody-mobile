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
        assertTrue(CreateSessionModel.machineChoosable(snapshot))
        val local = CreateSessionModel.select(snapshot, github.copy(machineId = "m2"), "m1:local:a").copy(worktree = true)
        assertEquals("m1", local.machineId)
        // Another computer has no local project here, so the target becomes a chat on it.
        val moved = CreateSessionModel.pickMachine(snapshot, local, "m2")
        assertTrue(moved.chat)
        assertEquals("m2", moved.machineId)
        assertEquals(listOf("github:lody/web"), CreateSessionModel.recent(snapshot, moved).map { it.id })
        assertFalse(CreateSessionModel.select(snapshot, local, "github:lody/web").worktree)
        val chat = CreateSessionModel.chat(snapshot, local)
        assertTrue(chat.chat)
        assertEquals("m1", chat.machineId)
    }

    @Test
    fun machineKindFollowsOsAndMacName() {
        val machines = snapshot.copy(
            machineIds = listOf("mb", "imac", "win", "box", "unknown"),
            machineNames = mapOf("mb" to "zx-MacBook-Pro", "imac" to "Studio iMac", "win" to "Desk", "box" to "build"),
            machineOs = mapOf("mb" to "darwin", "imac" to "darwin", "win" to "win32", "box" to "linux"),
        )
        fun kind(id: String?) = CreateSessionModel.machineKind(machines, id)
        assertEquals(CreateSessionModel.MachineKind.Laptop, kind("mb"))
        assertEquals(CreateSessionModel.MachineKind.Desktop, kind("imac"))
        assertEquals(CreateSessionModel.MachineKind.Desktop, kind("win"))
        assertEquals(CreateSessionModel.MachineKind.Server, kind("box"))
        assertEquals(CreateSessionModel.MachineKind.Laptop, kind("unknown"))
        assertEquals(CreateSessionModel.MachineKind.Laptop, kind(null))
    }
}
