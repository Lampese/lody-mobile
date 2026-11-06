@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package app.lody.android.diagnostics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.view.WindowCompat
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lody.android.designsystem.*

/** Offline design spike. No runtime, network writes, launcher entry or production route. */
class ExpressivePreviewActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val initial = intent.getStringExtra("screen") ?: "home"
        val night = intent.getBooleanExtra("dark", false)
        setContent { ExpressivePreview(initial, night) }
    }
}

@Composable
private fun ExpressivePreview(initial: String, night: Boolean) {
    var dark by remember { mutableStateOf(night) }
    var screen by remember { mutableStateOf(if (initial == "home") "home" else "chat") }
    var settings by remember { mutableStateOf(initial == "config") }
    var method by remember { mutableStateOf(false) }
    var merged by remember { mutableStateOf(false) }
    var running by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    val view = LocalView.current
    SideEffect {
        val window = (view.context as android.app.Activity).window
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
    LodyTheme(darkTheme = dark) {
        val base = MaterialTheme.colorScheme
        val surface = if (dark) LodyTheme.colors.bgRaised else base.surface
        val colors = base.copy(
            surface = surface, background = surface,
            primary = if (dark) LodyTheme.colors.accentFg else base.primary,
            onPrimary = if (dark) LodyTheme.colors.bgBase else base.onPrimary,
            primaryContainer = LodyTheme.colors.accentSubtle.compositeOver(surface),
            secondaryContainer = LodyTheme.colors.fillSecondary.compositeOver(surface),
            surfaceContainer = if (dark) LodyTheme.colors.fillSecondary.compositeOver(surface) else LodyTheme.colors.bgGrouped,
            surfaceContainerLow = if (dark) LodyTheme.colors.fillSecondary.compositeOver(surface) else LodyTheme.colors.bgGrouped,
            surfaceContainerHigh = LodyTheme.colors.fillSecondary.compositeOver(surface),
        )
        MaterialExpressiveTheme(colorScheme = colors, motionScheme = MotionScheme.expressive()) {
            Scaffold(
                topBar = {
                    Column(Modifier.statusBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("M3 Expressive · 试样", style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = { screen = if (screen == "home") "chat" else "home" }) {
                                Text(if (screen == "home") "查看对话" else "查看首页")
                            }
                            IconButton(onClick = { dark = !dark }) { Glyph(if (dark) LodyIcons.Sun else LodyIcons.Moon, "切换明暗") }
                        }
                        if (screen == "home") {
                            TopAppBar(title = { Text("Lody", fontWeight = FontWeight.SemiBold) }, actions = {
                                IconButton(onClick = { settings = true }) { Glyph(LodyIcons.Search, "搜索试样") }
                                IconButton(onClick = { settings = true }) { Glyph(LodyIcons.Filter, "筛选试样") }
                            })
                        } else {
                            TopAppBar(title = { Column {
                                Text("优化移动端对话体验", maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                                Text("lody-mobile-dev · 2 个仓库", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                            } }, navigationIcon = { IconButton(onClick = { screen = "home" }) { Glyph(LodyIcons.Back, "返回") } },
                                actions = { IconButton(onClick = { settings = true }) { Glyph(LodyIcons.More, "更多试样") } })
                        }
                    }
                },
                bottomBar = {
                    if (screen == "home") PreviewNavigation { screen = "chat" }
                    else Column(Modifier.navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalIconButton(onClick = { settings = true }) { Glyph(LodyIcons.Machine, "机器") }
                            AssistChip(onClick = { method = true }, label = { Text("#128  +56 −2") }, leadingIcon = { Glyph(LodyIcons.Branch) })
                            Spacer(Modifier.weight(1f))
                            SplitButtonLayout(leadingButton = {
                                SplitButtonDefaults.LeadingButton(onClick = { merged = !merged }, colors = ButtonDefaults.filledTonalButtonColors()) { Text(if (merged) "已合并" else "合并") }
                            }, trailingButton = {
                                SplitButtonDefaults.TrailingButton(onClick = { method = true }, colors = ButtonDefaults.filledTonalButtonColors()) { Glyph(LodyIcons.Expand, "合并选项") }
                            })
                        }
                        Surface(shape = RoundedCornerShape(28.dp), color = colors.surfaceContainerHigh) {
                            Column(Modifier.padding(8.dp)) {
                                TextField(value = prompt, onValueChange = { prompt = it }, modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("给 Lody 发消息…") }, minLines = 2, maxLines = 4,
                                    colors = TextFieldDefaults.colors(focusedContainerColor = colors.surfaceContainerHigh,
                                        unfocusedContainerColor = colors.surfaceContainerHigh,
                                        focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                        unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { settings = true }) { Glyph(LodyIcons.Plus, "添加附件试样") }
                                    TextButton(onClick = { settings = true }) {
                                        Glyph(LodyIcons.Agent); Spacer(Modifier.width(8.dp)); Text("6 Astra · Medium"); Glyph(LodyIcons.Expand)
                                    }
                                    Spacer(Modifier.weight(1f))
                                    FilledIconButton(onClick = { running = !running; prompt = "" }, shapes = IconButtonDefaults.shapes()) {
                                        Glyph(if (running) LodyIcons.Stop else LodyIcons.Send, if (running) "停止试运行" else "发送试运行")
                                    }
                                }
                            }
                        }
                    }
                },
                floatingActionButton = {
                    if (screen == "home") ExtendedFloatingActionButton(onClick = { screen = "chat" },
                        icon = { Glyph(LodyIcons.Compose) }, text = { Text("新对话") })
                },
            ) { padding ->
                if (screen == "home") PreviewHome(Modifier.padding(padding)) { screen = "chat" }
                else PreviewConversation(Modifier.padding(padding), running) { settings = true }
            }
            if (settings) RunConfigSheet { settings = false }
            if (method) ModalBottomSheet(onDismissRequest = { method = false }) {
                Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
                    Text("合并 Pull Request", style = MaterialTheme.typography.headlineSmall)
                    Text("#128 · 改进移动端对话体验", color = colors.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    listOf("Squash and merge", "Merge commit", "Rebase and merge").forEach { name ->
                        TextButton(onClick = { merged = true; method = false }, modifier = Modifier.fillMaxWidth()) { Text(name) }
                    }
                    Text("本地试样，不会操作真实 PR", style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable private fun Glyph(icon: ImageVector, description: String? = null) {
    Icon(icon, description, Modifier.size(20.dp))
}

@Composable private fun PreviewNavigation(open: () -> Unit) {
    var selected by remember { mutableIntStateOf(0) }
    NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        listOf("对话" to LodyIcons.Sessions, "项目" to LodyIcons.Folder, "设置" to LodyIcons.Settings).forEachIndexed { index, (name, icon) ->
            NavigationBarItem(selected = selected == index, onClick = { selected = index; if (index != 0) open() },
                icon = { Glyph(icon) }, label = { Text(name) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = MaterialTheme.colorScheme.primaryContainer, selectedIconColor = MaterialTheme.colorScheme.primary, selectedTextColor = MaterialTheme.colorScheme.primary))
        }
    }
}

@Composable private fun PreviewHome(modifier: Modifier, open: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("我的任务") }, leadingIcon = { Glyph(LodyIcons.Check) })
            FilterChip(selected = false, onClick = open, label = { Text("按时间") }, trailingIcon = { Glyph(LodyIcons.Expand) })
        }
        Text("今天", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
        listOf(
            Triple("优化移动端对话体验", "lody-mobile-dev · feature/mobile", "进行中"),
            Triple("支持多成员项目", "lody-mobile-dev · #128", "待审核"),
            Triple("新建对话支持图片附件", "lody-mobile · main", "12 分钟"),
        ).forEachIndexed { index, (title, detail, time) ->
            Surface(onClick = open, color = MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(16.dp)) {
                ListItem(headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    supportingContent = { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingContent = { Glyph(if (index == 0) LodyIcons.StateRunning else if (index == 1) LodyIcons.Branch else LodyIcons.Check) },
                    trailingContent = { Text(time, style = MaterialTheme.typography.labelSmall) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
            }
        }
        Text("昨天", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
        listOf("完善项目选择与机器切换", "对齐 Agent 配置", "优化消息滚动体验").forEach {
            ListItem(headlineContent = { Text(it) }, supportingContent = { Text("lody-mobile-dev") },
                leadingContent = { Glyph(LodyIcons.Session) }, modifier = Modifier.fillMaxWidth(),
                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface))
        }
        Spacer(Modifier.height(100.dp))
    }
}

@Composable private fun PreviewConversation(modifier: Modifier, running: Boolean, info: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Surface(Modifier.align(Alignment.End).widthIn(max = 300.dp), shape = RoundedCornerShape(24.dp), color = c.surfaceContainerHigh) {
            Text("保留现在的基础布局，让 Android 的按钮和交互更贴近 Material。", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Text("工作了 1分20秒  ›", color = c.onSurfaceVariant, style = MaterialTheme.typography.labelLarge)
        Text("让操作更清楚，让阅读更安静。", style = MaterialTheme.typography.titleLarge)
        Text("对话内容仍然占据主要空间。把更鲜明的形状、颜色和动效留给发送、选择和合并这些关键操作。", style = MaterialTheme.typography.bodyLarge)
        Surface(onClick = info, shape = RoundedCornerShape(20.dp), color = c.surfaceContainerLow) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Glyph(LodyIcons.File); Text("  6 个文件", modifier = Modifier.weight(1f)); Text("+56", color = LodyTheme.colors.diffAddedText); Text("  −2", color = LodyTheme.colors.diffRemovedText); Glyph(LodyIcons.Forward)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var copied by remember { mutableStateOf(false) }
            IconButton(onClick = { copied = !copied }) { Glyph(if (copied) LodyIcons.Check else LodyIcons.Copy, "复制试样") }
            IconButton(onClick = info) { Glyph(LodyIcons.Info, "信息试样") }
            IconButton(onClick = info) { Glyph(LodyIcons.Branch, "分叉试样") }
            Text("13:24", style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
        }
        if (running) Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Text("  正在试运行…", color = c.onSurfaceVariant)
        }
    }
}

@Composable private fun RunConfigSheet(close: () -> Unit) {
    var effort by remember { mutableIntStateOf(1) }
    var plan by remember { mutableStateOf(false) }
    var fast by remember { mutableStateOf(true) }
    ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("运行配置", style = MaterialTheme.typography.headlineMedium)
            Text("设置当前对话的 Agent 与思考方式", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Column {
                    ListItem(headlineContent = { Text("Agent") }, supportingContent = { Text("Codex") }, leadingContent = { Glyph(LodyIcons.Agent) })
                    HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                    ListItem(headlineContent = { Text("模型") }, supportingContent = { Text("6 Astra") }, trailingContent = { Glyph(LodyIcons.Check) })
                }
            }
            Text("思考强度", style = MaterialTheme.typography.titleSmall)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                listOf("Low", "Medium", "High").forEachIndexed { index, name ->
                    ToggleButton(checked = effort == index, onCheckedChange = { effort = index }, modifier = Modifier.weight(1f),
                        colors = ToggleButtonDefaults.toggleButtonColors(containerColor = MaterialTheme.colorScheme.surface),
                        shapes = when (index) { 0 -> ButtonGroupDefaults.connectedLeadingButtonShapes(); 2 -> ButtonGroupDefaults.connectedTrailingButtonShapes(); else -> ButtonGroupDefaults.connectedMiddleButtonShapes() }) {
                        Text(name)
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("计划模式"); Text("先梳理步骤，再开始执行", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = plan, onCheckedChange = { plan = it })
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("快速模式"); Text("优先响应速度", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                Switch(checked = fast, onCheckedChange = { fast = it })
            }
            Button(onClick = close, modifier = Modifier.fillMaxWidth().height(56.dp), shapes = ButtonDefaults.shapes()) { Text("完成") }
        }
    }
}
