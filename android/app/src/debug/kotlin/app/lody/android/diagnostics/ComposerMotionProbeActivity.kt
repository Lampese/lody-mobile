package app.lody.android.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
import app.lody.android.chat.ChatComposer
import app.lody.android.designsystem.LodyTheme

/** Real composer animation without requiring a connected session or writing account data. */
class ComposerMotionProbeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LodyTheme {
                var prompt by remember { mutableStateOf(TextFieldValue()) }
                Surface(color = LodyTheme.colors.bgBase) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding().imePadding(), contentAlignment = Alignment.BottomCenter) {
                        ChatComposer(
                            prompt = prompt, onPromptChange = { prompt = it }, onFocusChange = {}, enabled = true,
                            agentCliType = "builtin", agentType = "codex", roleEmoji = null, roleName = null,
                            agentName = "Codex", face = listOf("6.1 Sol", "Medium"), mode = null, modeLabel = null,
                            plan = false, fast = false, onRunConfig = {}, stoppable = false, stopping = false,
                            attachments = emptyList(), onPickPhotos = {}, onPickFiles = {}, onRemoveAttachment = {},
                            onSend = {}, onStop = {},
                        )
                    }
                }
            }
        }
    }
}
