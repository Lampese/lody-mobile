// Content-layer building blocks. Each mirrors a view in ios/App/Components.swift
// with the same tokens, so the two previews can be compared side by side.

package app.lody.designpreview

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.lody.android.designsystem.LodyElevation
import app.lody.android.designsystem.LodyElevations
import app.lody.android.designsystem.LodyIconSize
import app.lody.android.designsystem.LodyIcons
import app.lody.android.designsystem.LodyLayout
import app.lody.android.designsystem.LodyOpacity
import app.lody.android.designsystem.LodyRadius
import app.lody.android.designsystem.LodySize
import app.lody.android.designsystem.LodySpacing
import app.lody.android.designsystem.LodyStroke
import app.lody.android.designsystem.LodyTheme
import app.lody.android.designsystem.shadow

@Composable
fun Glyph(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = LodyIconSize.md) {
    Icon(icon, contentDescription = null, modifier = modifier.size(size), tint = tint)
}

@Composable
fun StateGlyph(state: SessionState, size: Dp = LodyIconSize.md) {
    if (state == SessionState.Running) {
        val angle by rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "angle",
        )
        Glyph(state.icon, state.color, Modifier.graphicsLayer { rotationZ = angle }, size)
    } else {
        Glyph(state.icon, state.color, size = size)
    }
}

@Composable
fun Hairline(leadingInset: Dp = 0.dp) {
    HorizontalDivider(
        modifier = Modifier.padding(start = leadingInset),
        thickness = Dp.Hairline,
        color = LodyTheme.colors.borderSubtle,
    )
}

/** Platform press feedback: Android ripples, iOS highlights. */
@Composable
fun SessionRow(session: SampleSession) {
    val colors = LodyTheme.colors
    val type = LodyTheme.typography
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgBase)
            .clickable {}
            .heightIn(min = LodySize.listRowTwoLine)
            .padding(horizontal = LodyLayout.listRowPaddingX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodyLayout.listRowGap),
    ) {
        StateGlyph(session.state)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(LodySpacing.xxs)) {
            Text(session.title, style = type.body, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.subtitle, style = type.footnote, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        session.label?.let { LabelChip(it) }
        Text(session.meta, style = type.footnote, color = colors.textTertiary)
    }
}

@Composable
fun LabelChip(label: LabelColor) {
    Row(
        modifier = Modifier
            .height(LodySize.chipSm)
            .background(label.bg, RoundedCornerShape(LodyRadius.sm))
            .padding(horizontal = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs + LodySpacing.xxs),
    ) {
        Box(Modifier.size(LodySize.dot).background(label.fg, CircleShape))
        Text(
            label.name,
            style = LodyTheme.typography.caption,
            color = label.fg,
        )
    }
}

@Composable
fun SectionHeader(title: String, count: Int? = null, showsAdd: Boolean = false) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LodySize.sectionHeader)
            .padding(horizontal = LodyLayout.listRowPaddingX),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Text(title, style = LodyTheme.typography.subhead, color = colors.textTertiary)
        if (count != null) Text("$count", style = LodyTheme.typography.subhead, color = colors.textPlaceholder)
        Spacer(Modifier.weight(1f))
        if (showsAdd) Glyph(LodyIcons.Plus, colors.iconTertiary)
    }
}

@Composable
fun FilterChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LodyTheme.colors
    Box(
        modifier = Modifier
            .height(LodySize.chip + LodySpacing.xs)
            .clip(CircleShape)
            .background(if (selected) colors.fillSelected else Color.Transparent)
            .border(LodyStroke.default, if (selected) Color.Transparent else colors.borderDefault, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = LodySpacing.md),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            style = LodyTheme.typography.subhead,
            color = if (selected) colors.textPrimary else colors.textSecondary,
        )
    }
}

@Composable
fun StatusBadge(state: SessionState) {
    val colors = LodyTheme.colors
    val (bg, fg) = when (state) {
        SessionState.Running -> colors.accentSubtle to colors.accentOnSubtle
        SessionState.Waiting -> colors.warningSubtle to colors.warningOnSubtle
        SessionState.Done, SessionState.Merged -> colors.successSubtle to colors.successOnSubtle
        SessionState.Failed -> colors.dangerSubtle to colors.dangerOnSubtle
        SessionState.Idle -> colors.fillSecondary to colors.textSecondary
    }
    Row(
        modifier = Modifier
            .height(LodySize.chipSm)
            .background(bg, RoundedCornerShape(LodyRadius.sm))
            .padding(horizontal = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.xs),
    ) {
        Glyph(state.icon, fg, size = LodyIconSize.sm)
        Text(state.label, style = LodyTheme.typography.caption, color = fg)
    }
}

enum class LodyButtonKind { Primary, Secondary, Destructive, Ghost }

@Composable
fun LodyButton(
    title: String,
    icon: ImageVector? = null,
    kind: LodyButtonKind = LodyButtonKind.Primary,
    enabled: Boolean = true,
    onClick: () -> Unit = {},
) {
    val colors = LodyTheme.colors
    val (bg, fg) = when (kind) {
        LodyButtonKind.Primary -> colors.accentDefault to colors.textOnAccent
        LodyButtonKind.Secondary -> colors.fillSecondary to colors.textPrimary
        LodyButtonKind.Destructive -> colors.dangerSolid to colors.textOnAccent
        LodyButtonKind.Ghost -> Color.Transparent to colors.textLink
    }
    val shape = RoundedCornerShape(LodyRadius.md)
    Row(
        modifier = Modifier
            .alpha(if (enabled) 1f else LodyOpacity.disabled)
            .height(LodySize.controlMd)
            .clip(shape)
            .background(bg, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = LodySpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        if (icon != null) Glyph(icon, fg, size = LodyIconSize.sm)
        Text(title, style = LodyTheme.typography.subhead, color = fg)
    }
}

@Composable
fun Toast(text: String) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    Row(
        modifier = Modifier
            .dropShadow(shape, LodyElevations.md.shadow())
            .height(LodySize.controlMd)
            .background(colors.bgInverse, shape)
            .padding(horizontal = LodySpacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Glyph(LodyIcons.Check, colors.textInverse)
        Text(text, style = LodyTheme.typography.subhead, color = colors.textInverse)
    }
}

@Composable
fun ElevationTile(name: String, elevation: LodyElevation) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    Column(
        modifier = Modifier
            .width(104.dp)
            .dropShadow(shape, elevation.shadow())
            .background(colors.bgOverlay, shape)
            .padding(LodySpacing.md),
        verticalArrangement = Arrangement.spacedBy(LodySpacing.xxs),
    ) {
        Text(name, style = LodyTheme.typography.footnote, color = colors.textPrimary)
        Text(
            "y ${elevation.y.value.toInt()} · blur ${elevation.blur.value.toInt()}",
            style = LodyTheme.typography.caption,
            color = colors.textTertiary,
        )
    }
}

@Composable
fun Composer(modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(LodyRadius.xl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = LodySize.composerMin)
            .background(colors.bgRaised, shape)
            .border(LodyStroke.default, colors.borderDefault, shape)
            .padding(start = LodySpacing.md, end = LodySpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Glyph(LodyIcons.Attach, colors.iconSecondary)
        PlainField(text, { text = it }, "Ask Lody…", Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(LodySize.controlSm)
                .clip(CircleShape)
                .background(colors.accentDefault)
                .clickable {},
            contentAlignment = Alignment.Center,
        ) {
            Glyph(LodyIcons.Send, colors.textOnAccent)
        }
    }
}

@Composable
private fun PlainField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    val colors = LodyTheme.colors
    val style = LodyTheme.typography.body
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        singleLine = true,
        textStyle = style.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.accentDefault),
        decorationBox = { inner ->
            Box {
                if (value.isEmpty()) Text(placeholder, style = style, color = colors.textPlaceholder)
                inner()
            }
        },
    )
}

@Composable
fun LodyTextField(placeholder: String) {
    val colors = LodyTheme.colors
    var text by remember { mutableStateOf("") }
    val shape = RoundedCornerShape(LodyRadius.md)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(LodySize.controlMd)
            .background(colors.bgSunken, shape)
            .border(LodyStroke.default, colors.borderDefault, shape)
            .padding(horizontal = LodySpacing.md),
        contentAlignment = Alignment.CenterStart,
    ) {
        PlainField(text, { text = it }, placeholder, Modifier.fillMaxWidth())
    }
}

@Composable
fun Banner(text: String) {
    val colors = LodyTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.warningSubtle, RoundedCornerShape(LodyRadius.md))
            .padding(LodySpacing.md),
        horizontalArrangement = Arrangement.spacedBy(LodySpacing.sm),
    ) {
        Glyph(LodyIcons.Alert, colors.warningDefault)
        Text(text, style = LodyTheme.typography.footnote, color = colors.warningOnSubtle)
    }
}

@Composable
fun CodeBlock(lines: List<List<Pair<SyntaxKind, String>>>) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    val spans = lines.map { line -> line.map { (kind, text) -> kind.color to text } }
    val highlighted = buildAnnotatedString {
        spans.forEachIndexed { index, line ->
            for ((color, text) in line) withStyle(SpanStyle(color = color)) { append(text) }
            if (index < spans.lastIndex) append("\n")
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.codeBg, shape)
            .border(LodyStroke.default, colors.codeBorder, shape)
            .horizontalScroll(rememberScrollState())
            .padding(LodySpacing.md),
    ) {
        Text(highlighted, style = LodyTheme.typography.code, softWrap = false)
    }
}

@Composable
fun DiffView(lines: List<Pair<DiffKind, String>>) {
    val colors = LodyTheme.colors
    val shape = RoundedCornerShape(LodyRadius.md)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.codeBg)
            .border(LodyStroke.default, colors.codeBorder, shape)
            .padding(vertical = LodySpacing.sm),
    ) {
        for ((kind, text) in lines) {
            val (bg, fg) = when (kind) {
                DiffKind.Hunk -> colors.diffHunkBg to colors.diffHunkText
                DiffKind.Context -> Color.Transparent to colors.codeText
                DiffKind.Added -> colors.diffAddedBg to colors.diffAddedText
                DiffKind.Removed -> colors.diffRemovedBg to colors.diffRemovedText
            }
            Text(
                text,
                style = LodyTheme.typography.codeSmall,
                color = fg,
                softWrap = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = LodySpacing.md),
            )
        }
    }
}

@Composable
fun EmptyState() {
    val colors = LodyTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = LodySpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(LodySpacing.md),
    ) {
        Box(
            modifier = Modifier
                .size(LodySize.avatarLg + LodySpacing.lg)
                .background(colors.fillTertiary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(LodyIcons.Inbox, colors.iconSecondary, size = LodyIconSize.xl)
        }
        Text("No sessions yet", style = LodyTheme.typography.headline, color = colors.textPrimary)
        Text(
            "Start a session from a project to see it here.",
            style = LodyTheme.typography.footnote,
            color = colors.textSecondary,
        )
        LodyButton("New session", LodyIcons.Plus)
    }
}

/** Titled card on bg.grouped, used by the Components and Tokens screens. */
@Composable
fun GroupCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(LodySpacing.sm)) {
        Text(
            title,
            style = LodyTheme.typography.subhead,
            color = LodyTheme.colors.textTertiary,
            modifier = Modifier.padding(horizontal = LodySpacing.xs),
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(LodyTheme.colors.bgRaised, RoundedCornerShape(LodyRadius.lg))
                .padding(LodySpacing.lg),
            verticalArrangement = Arrangement.spacedBy(LodySpacing.md),
            content = content,
        )
    }
}

/** Cycles system -> light -> dark so both modes can be reviewed on one device. */
@Composable
fun AppearanceButton(appearance: Int, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Glyph(if (appearance == 2) LodyIcons.Moon else LodyIcons.Sun, LodyTheme.colors.iconPrimary)
    }
}
