package com.anchorone.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics

/** Full-screen backdrop: a quiet vertical gradient with one soft overhead light. */
@Composable
fun AnchorBackground(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val c = Anchor.colors
    Box(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(c.bgTop, c.bgBottom)))
            .drawBehind {
                if (c.glow.alpha > 0f) {
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(c.glow.copy(alpha = if (c.isDark) 0.10f else 0.08f), Color.Transparent),
                            center = Offset(size.width * 0.5f, -size.height * 0.05f),
                            radius = size.width * 1.1f,
                        ),
                        radius = size.width * 1.1f,
                        center = Offset(size.width * 0.5f, -size.height * 0.05f),
                    )
                }
            },
        content = content,
    )
}

/** Standard non-session screen: back button, title, scrollable content, respects system bars. */
@Composable
fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    scroll: Boolean = true,
    action: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AnchorBackground(modifier) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Back", onBack)
                Text(
                    title,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    color = Anchor.colors.text,
                    modifier = Modifier.weight(1f).padding(start = 4.dp).semantics { heading() },
                )
                action?.invoke()
            }
            val inner = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp)
            Column(
                if (scroll) inner.verticalScroll(rememberScrollState()) else inner,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                content()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
fun IconAction(icon: ImageVector, label: String, onClick: () -> Unit, tint: Color = Anchor.colors.textDim) {
    Box(
        Modifier
            .size(52.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClickLabel = label, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp)) }
}

@Composable
fun CloseAction(onClick: () -> Unit, label: String = "Stop and exit") =
    IconAction(Icons.Filled.Close, label, onClick)

/** Pressable surface with a gentle spring scale. Haptic tick on press when enabled. */
@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(22.dp),
    color: Color = Anchor.colors.surface,
    border: BorderStroke? = BorderStroke(1.dp, Anchor.colors.outline),
    label: String? = null,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val env = Anchor.env
    val scale by animateFloatAsState(
        if (pressed && !env.reducedMotion) 0.97f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    val haptics = rememberHaptics()
    Box(
        modifier
            .scale(scale)
            .clip(shape)
            .background(color)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(
                interactionSource = interaction,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
            ) {
                haptics.tick()
                onClick()
            },
        content = content,
    )
}

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val c = Anchor.colors
    val tall = if (Anchor.env.simplified) 72.dp else 60.dp
    Pressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = tall),
        shape = RoundedCornerShape(30.dp),
        color = if (enabled) c.accent else c.surfaceHigh,
        border = null,
        enabled = enabled,
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (enabled) c.onAccent else c.textFaint,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun SecondaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Anchor.colors
    val tall = if (Anchor.env.simplified) 68.dp else 56.dp
    Pressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = tall),
        shape = RoundedCornerShape(28.dp),
        color = c.surface,
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = c.text,
            textAlign = TextAlign.Center,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 20.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = Anchor.colors.textDim) {
    Box(
        modifier
            .heightIn(min = 52.dp)
            .clip(RoundedCornerShape(26.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, color = color)
    }
}

/** Large two-line choice card used for pathway selection and menus. */
@Composable
fun ChoiceCard(
    title: String,
    detail: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accent: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val c = Anchor.colors
    val env = Anchor.env
    Pressable(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().heightIn(min = if (env.simplified) 84.dp else 72.dp),
        color = if (accent) c.accentSoft else c.surface,
        border = BorderStroke(1.dp, if (accent) c.accent.copy(alpha = 0.6f) else c.outline),
        label = title,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = if (env.simplified) androidx.compose.material3.MaterialTheme.typography.titleLarge
                    else androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    color = if (accent) c.accent else c.text,
                )
                if (detail != null && !env.minimal) {
                    Spacer(Modifier.height(3.dp))
                    Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = c.textDim)
                }
            }
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Anchor.colors
    Pressable(
        onClick = onClick,
        modifier = modifier.heightIn(min = 48.dp),
        shape = RoundedCornerShape(24.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(1.dp, if (selected) c.accent else c.outline),
        label = text,
    ) {
        Text(
            text,
            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
            color = if (selected) c.accent else c.text,
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 18.dp, vertical = 12.dp),
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        color = Anchor.colors.textFaint,
        modifier = modifier.padding(top = 10.dp).semantics { heading() },
    )
}

@Composable
fun BodyText(text: String, modifier: Modifier = Modifier, color: Color = Anchor.colors.textDim) {
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.bodyLarge, color = color, modifier = modifier)
}

@Composable
fun Card(modifier: Modifier = Modifier, padding: PaddingValues = PaddingValues(20.dp), content: @Composable ColumnScope.() -> Unit) {
    val c = Anchor.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(24.dp))
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Anchor.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = c.text)
            if (detail != null) Text(detail, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = c.textDim)
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = c.onAccent, checkedTrackColor = c.accent,
                uncheckedThumbColor = c.textDim, uncheckedTrackColor = c.surfaceHigh, uncheckedBorderColor = c.outline,
            ),
        )
    }
}

/** Segmented single-choice selector with large targets. */
@Composable
fun <T> Segmented(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    val c = Anchor.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(c.surface)
            .border(1.dp, c.outline, RoundedCornerShape(26.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { o ->
            val on = o == selected
            Box(
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (on) c.accentSoft else Color.Transparent)
                    .clickable(role = Role.RadioButton) { onSelect(o) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(o),
                    style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                    color = if (on) c.accent else c.textDim,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** Gentle progress beads — shows position, never a score. */
@Composable
fun Beads(total: Int, done: Int, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val c = Anchor.colors
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(total) { i ->
            Box(
                Modifier
                    .size(if (i == done) size * 1.5f else size)
                    .clip(CircleShape)
                    .background(if (i < done) c.accent.copy(alpha = 0.8f) else if (i == done) c.accent else c.outline),
            )
        }
    }
}
