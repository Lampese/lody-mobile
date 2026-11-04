package app.lody.android.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lody.android.R
import app.lody.android.designsystem.*
import app.lody.android.runtime.MentionCandidate
import app.lody.android.runtime.MentionCatalogPage

/** One level of the mention panel: the categories (or typed matches), one category, or a folder. */
sealed interface MentionLevel {
    data object Root : MentionLevel
    data class Category(val category: String) : MentionLevel
    data class Directory(val path: String) : MentionLevel
}

/**
 * The composer's @ references, after iOS `ChatMentionPanel` (Notion-style): typing a trigger
 * (`@`, `$` for skills, `/` for commands at the start) opens a panel docked on the keyboard
 * under the prompt; the text after the trigger filters the visible level.
 */
object Mentions {
    val categories = listOf("file", "skill", "session", "role", "issue", "pr", "cmd")

    /** The trigger the caret is in: where it starts, the query after it, and its scope. */
    data class Trigger(val start: Int, val end: Int, val query: String, val scoped: String?)

    private val token = Regex("(?:^|\\s)([@$][^\\s@$]*)$|^(/[^\\s/]*)$")

    fun trigger(value: TextFieldValue): Trigger? {
        val selection = value.selection
        // Keyboards compose Latin words too (Gboard), so a composing query still filters.
        if (!selection.collapsed) return null
        val caret = selection.start
        if (caret <= 0 || caret > value.text.length) return null
        val match = token.find(value.text.substring(0, caret)) ?: return null
        val group = match.groups[1] ?: match.groups[2] ?: return null
        var query = group.value.drop(1)
        var scoped: String? = when (group.value.first()) {
            '$' -> "skill"
            '/' -> "cmd"
            else -> null
        }
        val colon = query.indexOf(':')
        if (scoped == null && colon > 0 && query.substring(0, colon) in categories) {
            scoped = query.substring(0, colon)
            query = query.substring(colon + 1)
        }
        return Trigger(group.range.first, caret, query, scoped)
    }

    private fun parent(path: String) = path.substringBeforeLast('/', "")

    private fun matches(item: MentionCandidate, query: String) =
        query.isEmpty() || "${item.name} ${item.path} ${item.subtitle}".contains(query, ignoreCase = true)

    /** The rows one level shows; root categories are `kind = "category"` with `path` = category. */
    fun rows(level: MentionLevel, catalogs: Map<String, MentionCatalogPage>, trigger: Trigger): List<MentionCandidate> {
        val items = categories.flatMap { catalogs[it]?.items.orEmpty() }
        val query = trigger.query
        return when (level) {
            MentionLevel.Root ->
                if (query.isEmpty() && trigger.scoped == null) {
                    categories.filter { it == "file" || it == "skill" || it == "session" || catalogs[it]?.items?.isNotEmpty() == true }
                        .map { MentionCandidate(it, it, "category") }
                } else items.filter { (trigger.scoped == null || it.category == trigger.scoped) && matches(it, query) }
            is MentionLevel.Category -> {
                val scoped = items.filter { it.category == level.category }
                if (level.category == "file" && query.isEmpty()) scoped.filter { parent(it.path).isEmpty() }
                else scoped.filter { matches(it, query) }
            }
            is MentionLevel.Directory -> {
                val files = items.filter { it.category == "file" }
                if (query.isEmpty()) files.filter { parent(it.path) == level.path }
                else files.filter { it.path.startsWith(level.path + "/") && matches(it, query) }
            }
        }.distinctBy { it.path to it.kind }
    }

    /** Replaces the trigger with the reference and a trailing space, caret after it. */
    fun insert(value: TextFieldValue, trigger: Trigger, item: MentionCandidate): TextFieldValue {
        val reference = (item.insertText ?: "@${item.path}") + " "
        val text = value.text.replaceRange(trigger.start, trigger.end, reference)
        return TextFieldValue(text, TextRange(trigger.start + reference.length))
    }

    fun label(category: String): Int = when (category) {
        "file" -> R.string.mention_files
        "skill" -> R.string.mention_skills
        "session" -> R.string.mention_sessions
        "role" -> R.string.mention_roles
        "issue" -> R.string.mention_issues
        "pr" -> R.string.mention_prs
        else -> R.string.mention_commands
    }

    fun icon(kind: String): ImageVector = when (kind) {
        "directory", "file" -> if (kind == "directory") LodyIcons.Folder else LodyIcons.File
        "skill" -> LodyIcons.Sparkles
        "session" -> LodyIcons.Sessions
        "role" -> LodyIcons.User
        "issue" -> LodyIcons.Alert
        "pr" -> LodyIcons.PullRequest
        "cmd" -> LodyIcons.Terminal
        else -> LodyIcons.File
    }
}

/**
 * The panel itself, docked between the prompt and the keyboard. It never takes focus, so the
 * keyboard stays up; categories and folders push the next level, sliding in from the end (back
 * slides it away), with a header carrying back and the level's title.
 */
@Composable
fun MentionPanel(
    stack: List<MentionLevel>,
    rowsFor: (MentionLevel) -> List<MentionCandidate>,
    loading: (MentionLevel) -> Boolean,
    onPush: (MentionLevel) -> Unit,
    onPop: () -> Unit,
    onInsert: (MentionCandidate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    val haptics = LocalHapticFeedback.current
    BackHandler(enabled = stack.size > 1, onBack = onPop)
    Column(
        modifier
            .fillMaxWidth()
            .height(MentionPanelHeight)
            .clip(RoundedCornerShape(topStart = LodyRadius.xl, topEnd = LodyRadius.xl))
            .background(colors.bgRaised),
    ) {
        HorizontalDivider(color = colors.borderSubtle, thickness = 0.5.dp)
        AnimatedContent(
            targetState = stack,
            transitionSpec = {
                val forward = targetState.size >= initialState.size
                val duration = 280
                if (forward) {
                    (slideInHorizontally(tween(duration)) { it } + fadeIn(tween(duration)))
                        .togetherWith(slideOutHorizontally(tween(duration)) { -it / 4 } + fadeOut(tween(duration)))
                } else {
                    (slideInHorizontally(tween(duration)) { -it / 4 } + fadeIn(tween(duration)))
                        .togetherWith(slideOutHorizontally(tween(duration)) { it } + fadeOut(tween(duration)))
                }.using(SizeTransform(clip = true))
            },
            contentKey = { it.size to it.lastOrNull() },
            label = "mention-level",
        ) { levels ->
            val level = levels.last()
            Column(Modifier.fillMaxSize()) {
                if (levels.size > 1) {
                    val back = stringResource(R.string.mention_back)
                    Row(Modifier.fillMaxWidth().heightIn(min = LodySize.controlLg), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onPop, modifier = Modifier.semantics { contentDescription = back }) {
                            Icon(LodyIcons.Back, contentDescription = null, tint = colors.iconPrimary, modifier = Modifier.size(LodyIconSize.md))
                        }
                        Text(
                            when (level) {
                                is MentionLevel.Category -> stringResource(Mentions.label(level.category))
                                is MentionLevel.Directory -> level.path.substringAfterLast('/')
                                MentionLevel.Root -> ""
                            },
                            style = type.headline, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                    HorizontalDivider(color = colors.borderSubtle, thickness = 0.5.dp)
                }
                val rows = rowsFor(level)
                LazyColumn(Modifier.fillMaxSize()) {
                    if (level is MentionLevel.Directory) {
                        item(key = "use-directory") {
                            MentionRow(
                                icon = LodyIcons.Folder, title = stringResource(R.string.mention_directory, level.path.substringAfterLast('/')),
                                subtitle = "", accent = true, disclosure = false,
                            ) { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onInsert(MentionCandidate(level.path, level.path.substringAfterLast('/'), "directory")) }
                        }
                    }
                    items(rows, key = { it.kind + "\n" + it.path }) { item ->
                        val browse = item.kind == "category" || item.kind == "directory"
                        val title = when (item.kind) {
                            "category" -> stringResource(Mentions.label(item.path))
                            "file", "directory" -> item.path.substringAfterLast('/')
                            "skill", "cmd" -> item.insertText ?: item.name
                            else -> item.name
                        }
                        val subtitle = when (item.kind) {
                            "file", "directory" -> item.path.substringBeforeLast('/', "")
                            else -> item.subtitle
                        }
                        MentionRow(
                            icon = Mentions.icon(if (item.kind == "category") item.path else item.kind),
                            title = title, subtitle = subtitle, accent = false, disclosure = browse,
                        ) {
                            when (item.kind) {
                                "category" -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPush(MentionLevel.Category(item.path)) }
                                "directory" -> { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); onPush(MentionLevel.Directory(item.path)) }
                                else -> { haptics.performHapticFeedback(HapticFeedbackType.Confirm); onInsert(item) }
                            }
                        }
                    }
                    if (rows.isEmpty() && level !is MentionLevel.Directory) {
                        item(key = "empty") {
                            Box(Modifier.fillMaxWidth().padding(vertical = LodySpacing.xl), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(if (loading(level)) R.string.mention_loading else R.string.mention_empty),
                                    style = type.subhead, color = colors.textSecondary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The panel's height while shown; the keyboard sits right below it. */
val MentionPanelHeight = 280.dp

@Composable
private fun MentionRow(icon: ImageVector, title: String, subtitle: String, accent: Boolean, disclosure: Boolean, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.listRow)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = LodyLayout.screenPaddingX, vertical = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Icon(icon, contentDescription = null, tint = if (accent) colors.accentFg else colors.iconSecondary, modifier = Modifier.size(LodyIconSize.md))
        Column(Modifier.weight(1f)) {
            Text(title, style = type.body, color = if (accent) colors.accentFg else colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotEmpty()) {
                Text(subtitle, style = type.footnote, color = colors.textTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (disclosure) Icon(LodyIcons.Forward, contentDescription = null, tint = colors.iconTertiary, modifier = Modifier.size(LodyIconSize.sm))
    }
}
