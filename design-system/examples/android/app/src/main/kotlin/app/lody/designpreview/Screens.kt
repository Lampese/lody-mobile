package app.lody.designpreview

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyStroke
import app.lody.android.designsystem.LodyTheme

/** Native large top app bar (Material) standing in for the iOS large-title navigation bar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Screen(
    title: String,
    background: Color,
    appearance: Int,
    onToggleAppearance: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(title) },
                actions = {
                    AppearanceButton(appearance, onToggleAppearance)
                    extraAction?.invoke()
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = background,
                    scrolledContainerColor = background,
                    titleContentColor = LodyTheme.colors.textPrimary,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
        bottomBar = bottomBar,
        content = content,
    )
}

@Composable
fun SessionsScreen(appearance: Int, onToggleAppearance: () -> Unit) {
    var filter by remember { mutableStateOf(SampleData.filters.first()) }
    Screen(
        title = "Sessions",
        background = LodyTheme.colors.bgBase,
        appearance = appearance,
        onToggleAppearance = onToggleAppearance,
        extraAction = {
            IconButton(onClick = {}) { Glyph(LodyIcons.Compose, LodyTheme.colors.iconPrimary) }
        },
        bottomBar = {
            Composer(
                Modifier
                    .background(LodyTheme.colors.bgBase)
                    .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize(), contentPadding = padding) {
            item {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = LodyLayout.screenPaddingX)
                        .padding(bottom = LodySpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                ) {
                    SampleData.filters.forEach { item ->
                        FilterChip(item, item == filter) { filter = item }
                    }
                }
            }
            for (section in SampleData.sections) {
                item { SectionHeader(section.title, section.sessions.size, showsAdd = true) }
                items(section.sessions) { session ->
                    SessionRow(session)
                    Hairline(LodyLayout.listRowPaddingX + LodyIconSize.md + LodyLayout.listRowGap)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ComponentsScreen(appearance: Int, onToggleAppearance: () -> Unit) {
    var notify by remember { mutableStateOf(true) }
    var mode by remember { mutableStateOf("Agent") }
    val colors = LodyTheme.colors
    Screen("Components", colors.bgGrouped, appearance, onToggleAppearance) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(LodyLayout.screenPaddingX),
            verticalArrangement = Arrangement.spacedBy(LodyLayout.sectionGap),
        ) {
            GroupCard("Buttons") {
                Row(horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                    LodyButton("Run", LodyIcons.Send)
                    LodyButton("Cancel", kind = LodyButtonKind.Secondary)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                    LodyButton("Delete", LodyIcons.Trash, LodyButtonKind.Destructive)
                    LodyButton("View diff", kind = LodyButtonKind.Ghost)
                }
            }
            GroupCard("Status") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                ) {
                    SessionState.entries.forEach { StatusBadge(it) }
                }
            }
            GroupCard("Native controls (platform)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Notify when done",
                        style = LodyTheme.typography.body,
                        color = colors.textPrimary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = notify, onCheckedChange = { notify = it })
                }
                val modes = listOf("Agent", "Plan", "Ask")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    modes.forEachIndexed { index, item ->
                        SegmentedButton(
                            selected = mode == item,
                            onClick = { mode = item },
                            shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        ) { Text(item) }
                    }
                }
            }
            GroupCard("Inputs") {
                LodyTextField("Session name")
                Banner("Agent needs approval to run `pnpm install` in lody-mobile.")
            }
            GroupCard("Code") {
                CodeBlock(SampleData.code)
                DiffView(SampleData.diff)
            }
            GroupCard("Empty state") {
                EmptyState()
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TokensScreen(appearance: Int, onToggleAppearance: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    Screen("Tokens", colors.bgGrouped, appearance, onToggleAppearance) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(LodyLayout.screenPaddingX),
            verticalArrangement = Arrangement.spacedBy(LodyLayout.sectionGap),
        ) {
            GroupCard("Type") {
                for ((name, style) in type.entries()) {
                    Column(verticalArrangement = Arrangement.spacedBy(LodySpacing.xxs)) {
                        Text(
                            "$name · ${style.fontSize.value.toInt()}/${style.lineHeight.value.toInt()}",
                            style = type.caption,
                            color = colors.textTertiary,
                        )
                        Text("同一个 worktree 多个 tabs", style = style, color = colors.textPrimary)
                    }
                }
            }
            GroupCard("Icons") {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(LodySpacing.md),
                ) {
                    for ((name, icon) in LodyIcons.all) {
                        Column(
                            modifier = Modifier.width(72.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
                        ) {
                            Glyph(icon, colors.iconPrimary, size = LodyIconSize.lg)
                            Text(name, style = type.micro, color = colors.textTertiary, maxLines = 1)
                        }
                    }
                }
            }
            GroupCard("Color") {
                for ((name, color) in colors.entries()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                    ) {
                        val shape = RoundedCornerShape(LodyRadius.sm)
                        Box(
                            Modifier
                                .size(LodySize.avatarMd)
                                .background(color, shape)
                                .border(LodyStroke.default, colors.borderDefault, shape),
                        )
                        Text(name, style = type.code, color = colors.textPrimary)
                    }
                }
            }
            GroupCard("Spacing") {
                for ((name, value) in LodySpacing.all) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
                    ) {
                        Text(
                            "$name ${value.value.toInt()}",
                            style = type.codeSmall,
                            color = colors.textSecondary,
                            modifier = Modifier.width(80.dp),
                        )
                        Box(
                            Modifier
                                .width(value)
                                .height(LodySpacing.sm)
                                .background(colors.accentDefault),
                        )
                    }
                }
            }
            GroupCard("Radius") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
                    for ((name, value) in LodyRadius.all.filter { it.first != "full" }) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(LodySpacing.xs),
                        ) {
                            Box(
                                Modifier
                                    .size(56.dp)
                                    .border(LodyStroke.focus, colors.borderStrong, RoundedCornerShape(value)),
                            )
                            Text(name, style = type.micro, color = colors.textTertiary)
                        }
                    }
                }
            }
        }
    }
}
