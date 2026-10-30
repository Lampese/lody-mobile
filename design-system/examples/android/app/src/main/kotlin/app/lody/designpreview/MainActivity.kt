package app.lody.designpreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.lody.android.designsystem.LodyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 0 = system, 1 = light, 2 = dark; chosen from the list's More menu, as on iOS.
            var appearance by rememberSaveable { mutableIntStateOf(0) }
            val systemDark = isSystemInDarkTheme()
            val dark = when (appearance) {
                1 -> false
                2 -> true
                else -> systemDark
            }
            DisposableEffect(dark) {
                val transparent = android.graphics.Color.TRANSPARENT
                val style = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
                onDispose {}
            }
            LodyTheme(darkTheme = dark) {
                PreviewApp(appearance) { appearance = it }
            }
        }
    }
}

@Composable
private fun PreviewApp(appearance: Int, onAppearanceChange: (Int) -> Unit) {
    var destination by rememberSaveable { mutableStateOf<PreviewDestination?>(null) }
    BackHandler(enabled = destination != null) { destination = null }
    Box(Modifier.fillMaxSize().background(LodyTheme.colors.bgBase)) {
        when (destination) {
            null -> SessionListScreen(appearance, onAppearanceChange) { destination = it }
            PreviewDestination.Components -> ComponentsScreen { destination = null }
            PreviewDestination.Tokens -> TokensScreen { destination = null }
        }
    }
}
