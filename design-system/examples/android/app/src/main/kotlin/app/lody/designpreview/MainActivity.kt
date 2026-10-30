package app.lody.designpreview

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 0 = system, 1 = light, 2 = dark; matches the iOS preview's AppearanceButton.
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
                PreviewApp(appearance) { appearance = (appearance + 1) % 3 }
            }
        }
    }
}

private val tabs = listOf(
    "Sessions" to LodyIcons.Sessions,
    "Components" to LodyIcons.Sparkles,
    "Tokens" to LodyIcons.Settings,
)

@Composable
private fun PreviewApp(appearance: Int, onToggleAppearance: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val colors = LodyTheme.colors
    Scaffold(
        containerColor = colors.bgBase,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(containerColor = colors.bgBase) {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = tab == index,
                        onClick = { tab = index },
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(LodyIconSize.lg)) },
                        label = { Text(label, style = LodyTheme.typography.micro) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = colors.accentDefault,
                            selectedTextColor = colors.accentDefault,
                            indicatorColor = colors.accentSubtle,
                            unselectedIconColor = colors.iconSecondary,
                            unselectedTextColor = colors.textSecondary,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (tab) {
                0 -> SessionsScreen(appearance, onToggleAppearance)
                1 -> ComponentsScreen(appearance, onToggleAppearance)
                else -> TokensScreen(appearance, onToggleAppearance)
            }
        }
    }
}
