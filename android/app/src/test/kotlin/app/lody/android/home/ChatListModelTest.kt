package app.lody.android.home

import app.lody.android.runtime.CatalogProject
import app.lody.android.runtime.CatalogSession
import app.lody.android.runtime.CatalogSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatListModelTest {
    private fun session(id: String, project: String, status: String = "idle", archived: Boolean = false, pinned: Boolean = false, at: String) =
        CatalogSession(id, "m", project, "Chat $id", status, archived, pinned, at)

    private val snapshot = CatalogSnapshot(
        revision = 1,
        projects = listOf(
            CatalogProject("p-zed", "m", "zed", "/z"),
            CatalogProject("p-alpha", "m", "Alpha", "/a"),
            CatalogProject("p-empty", "m", "empty", "/e"),
            CatalogProject("m:unassigned", "m", "", ""),
        ),
        sessions = listOf(
            session("1", "p-zed", at = "2026-10-01T00:00:00Z"),
            session("2", "p-zed", status = "running", at = "2026-10-03T00:00:00Z"),
            session("3", "p-alpha", status = "waiting", at = "2026-10-02T00:00:00Z"),
            session("4", "p-alpha", archived = true, at = "2026-10-04T00:00:00Z"),
            session("5", "p-zed", pinned = true, at = "2026-10-05T00:00:00Z"),
            session("6", "m:unassigned", status = "error", at = "2026-10-06T00:00:00Z"),
        ),
        machineIds = listOf("m"),
    )

    @Test
    fun groupsPinnedFirstProjectsByNameChatsLastAndHidesArchivedAndEmpty() {
        val groups = ChatListModel.groups(snapshot)
        assertEquals(listOf("pinned", "p-alpha", "p-zed", "chats"), groups.map { it.id })
        assertEquals(listOf("5"), groups[0].chats.map { it.id })
        assertEquals(listOf("3"), groups[1].chats.map { it.id })
        // Newest first within a group.
        assertEquals(listOf("2", "1"), groups[2].chats.map { it.id })
    }

    @Test
    fun mapsStatusesToAttentionAndRanksWaitingAboveRunning() {
        val groups = ChatListModel.groups(snapshot).associateBy { it.id }
        assertEquals(ChatAttention.Running, groups.getValue("p-zed").chats.first().attention)
        assertNull(groups.getValue("p-zed").chats.last().attention)
        assertEquals(ChatAttention.Failed, groups.getValue("chats").chats.single().attention)
        val mixed = ChatGroup("x", "x", ChatGroup.Kind.Project, listOf(ChatItem("a", "a", ChatAttention.Running), ChatItem("b", "b", ChatAttention.Waiting)))
        assertEquals(ChatAttention.Waiting, mixed.mostUrgent)
    }

    @Test
    fun searchKeepsOnlyMatchingChatsAndDropsEmptyGroups() {
        val groups = ChatListModel.groups(snapshot)
        val result = ChatListModel.search(groups, " chat 3 ")
        assertEquals(listOf("p-alpha"), result.map { it.id })
        assertEquals(groups, ChatListModel.search(groups, "  "))
    }
}
