package com.anchorone.app.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.Screen
import com.anchorone.app.ui.components.AnchorBackground
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.IconAction
import com.anchorone.app.ui.components.Pressable
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun HomeScreen(vm: AppViewModel, prefs: UserPrefs) {
    val env = Anchor.env
    val c = Anchor.colors
    val resumable by vm.resumable.collectAsStateWithLifecycle()
    val canResume = resumable?.let { System.currentTimeMillis() - it.second < AppViewModel.RESUME_WINDOW_MS } == true

    AnchorBackground {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!prefs.quietMode) {
                    Text(
                        "ANCHOR ONE",
                        style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 4.sp),
                        color = c.textFaint,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                } else {
                    Text("Quiet mode", style = MaterialTheme.typography.labelMedium, color = c.textFaint, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(Modifier.weight(1f))
                IconAction(Icons.Filled.Settings, "Settings", { vm.navigate(Screen.SETTINGS) })
            }

            if (canResume) {
                Pressable(
                    onClick = { vm.resume() },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp).heightIn(min = 56.dp),
                    color = c.accentSoft,
                    label = "Resume where you left off",
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Resume where you left off", style = MaterialTheme.typography.titleSmall, color = c.accent, modifier = Modifier.weight(1f))
                        Text(
                            "Dismiss",
                            style = MaterialTheme.typography.labelLarge,
                            color = c.textDim,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { vm.dismissResume() }.padding(8.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            HelpOrb(
                label = if (prefs.quietMode) "Start" else "I NEED\nHELP NOW",
                size = when {
                    prefs.quietMode -> 200.dp
                    env.minimal -> 290.dp
                    env.simplified -> 270.dp
                    else -> 250.dp
                },
                onClick = { vm.helpNow() },
            )

            if (!env.simplified && !prefs.quietMode) {
                Spacer(Modifier.height(18.dp))
                Text(
                    "One tap. No sign-in. Works offline.",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.textFaint,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.weight(1f))

            when {
                env.minimal -> Unit
                env.simplified -> {
                    ChoiceCard("Toolkit", null, { vm.navigate(Screen.TOOLKIT) })
                    Spacer(Modifier.height(10.dp))
                }
                else -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        HomeTile("Toolkit", "All activities", Modifier.weight(1f)) { vm.navigate(Screen.TOOLKIT) }
                        HomeTile("Companion", "Audio guide", Modifier.weight(1f)) { vm.openCompanion() }
                        HomeTile("My card", "Your reminders", Modifier.weight(1f)) { vm.navigate(Screen.SUPPORT_CARD) }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                GhostButton("Safety & emergency", { vm.navigate(Screen.SAFETY) }, color = c.alert.copy(alpha = 0.9f))
                if (!env.minimal) {
                    GhostButton(if (prefs.quietMode) "Quiet: on" else "Quiet mode", { vm.navigate(Screen.QUIET) })
                }
            }
        }
    }
}

@Composable
private fun HomeTile(title: String, detail: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Anchor.colors
    Pressable(onClick = onClick, modifier = modifier.heightIn(min = 84.dp), label = title) {
        Column(Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = c.textDim)
        }
    }
}

/**
 * The central activation control. Slow, soft light rings drift outward; they are ambient only
 * (not a breathing pacer) and become fully static with reduced motion.
 */
@Composable
fun HelpOrb(label: String, size: Dp, onClick: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) 0.95f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "orbPress",
    )
    val animate = !env.reducedMotion && !env.quiet
    val phase = if (animate) {
        val t = rememberInfiniteTransition(label = "orb")
        val v by t.animateFloat(
            0f, 1f,
            infiniteRepeatable(tween((9000 / env.motionScale).toInt(), easing = LinearEasing)),
            label = "orbPhase",
        )
        v
    } else 0.3f

    Box(
        Modifier
            .size(size * 1.25f)
            .scale(scale)
            .clip(CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button) {
                haptics.confirm()
                onClick()
            }
            .semantics { contentDescription = label.replace("\n", " ") + ". Opens help options." },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val core = size.toPx() / 2
            if (c.glow.alpha > 0f) {
                drawCircle(
                    Brush.radialGradient(listOf(c.glow.copy(alpha = 0.22f), Color.Transparent), center, core * 1.25f),
                    radius = core * 1.25f, center = center,
                )
            }
            // Outward drifting rings.
            for (i in 0 until 3) {
                val p = ((phase + i / 3f) % 1f)
                val r = core * (1f + 0.22f * p)
                val a = (if (animate) (1f - p) else 0.6f - i * 0.18f) * 0.35f
                drawCircle(c.accent.copy(alpha = a.coerceIn(0f, 1f)), radius = r, center = center, style = Stroke(1.2.dp.toPx()))
            }
            // Core body.
            val shimmer = if (animate) (sin(phase * 2 * PI).toFloat() * 0.04f) else 0f
            drawCircle(
                Brush.radialGradient(
                    listOf(c.accent.copy(alpha = 0.28f + shimmer), c.teal.copy(alpha = 0.10f), c.surface.copy(alpha = 0.9f)),
                    center = Offset(center.x, center.y - core * 0.35f),
                    radius = core * 1.4f,
                ),
                radius = core, center = center,
            )
            drawCircle(c.accent.copy(alpha = 0.75f), radius = core, center = center, style = Stroke(1.6.dp.toPx()))
            drawCircle(c.accent.copy(alpha = 0.18f), radius = core * 0.86f, center = center, style = Stroke(1.dp.toPx()))
        }
        Text(
            label,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 2.5.sp, lineHeight = 30.sp),
            color = c.text,
            textAlign = TextAlign.Center,
        )
    }
}
