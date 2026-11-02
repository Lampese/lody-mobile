package app.lody.android.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.lody.android.create.*
import app.lody.android.designsystem.*
import app.lody.android.runtime.*
import kotlinx.coroutines.delay

/** Offline UI fixture: production page, deterministic service, no account or network access. */
class CreateSessionProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val result = intent.getStringExtra("result") ?: "accepted"
        val branchMode = intent.getStringExtra("branches")
        var calls = 0
        val service = object : CreationService {
            override suspend fun cachedBranches(scope: RuntimeScope, repo: String) =
                if (branchMode == "cold") null else BranchCache(BranchPage(listOf("main", "feature/cached"), "main"), "feature/cached")
            override suspend fun branches(scope: RuntimeScope, repo: String, page: Int): BranchPage {
                delay(12000)
                check(branchMode != "failed") { "fixture_offline" }
                return BranchPage(listOf("main", "feature/cached", "feature/refreshed"), "main")
            }
            override suspend fun options(scope: RuntimeScope, projectId: String?) = CreationOptions(
                listOf(CreationAgent("agent", "Fixture assistant", "machine", "builtin", "fixture")),
                listOf(CreationCapability("machine", "builtin", "fixture", listOf(CreationChoice("model", "Fixture model")), listOf(CreationChoice("auto", "Automatic")), mapOf("model" to listOf("low", "high")))),
            )
            override suspend fun create(scope: RuntimeScope, request: CreateRequest): CreationReceipt {
                check(++calls == 1) { "Duplicate creation" }
                check(request.text.isNotBlank())
                delay(700)
                return CreationReceipt(request.sessionId, result)
            }
        }
        val catalog = CatalogSnapshot(1, if (branchMode == null) emptyList() else listOf(CatalogProject("github:Fixture/Repo", "", "Fixture/Repo", "")), emptyList(), listOf("machine"), mapOf("machine" to "Fixture computer"))
        setContent {
            LodyTheme {
                var receipt by remember { mutableStateOf<String?>(null) }
                if (receipt == null) CreateSessionScreen(catalog, service, RuntimeScope("fixture", "workspace"), onCreated = { _, title -> receipt = "Created: $title" }, onBack = { finish() })
                else Box(Modifier.fillMaxSize().statusBarsPadding().padding(LodyLayout.screenPaddingX)) { Text(receipt!!, style = LodyTheme.typography.body) }
            }
        }
    }
}
