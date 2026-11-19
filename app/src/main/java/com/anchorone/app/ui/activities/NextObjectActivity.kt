package com.anchorone.app.ui.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anchorone.app.content.NextObjectPrompts
import com.anchorone.app.ui.components.Beads
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.components.SecondaryButton
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics

/**
 * THE NEXT OBJECT — attention moves out into the real room, one concrete prompt at a time.
 * Progression is entirely by touch; there is no timer.
 */
@Composable
fun NextObjectActivity(seed: Int, onDone: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    var round by rememberSaveable { mutableIntStateOf(0) }
    var index by rememberSaveable { mutableIntStateOf(0) }
    val prompts = remember(round) { mutableStateListOf<NextObjectPrompts.Prompt>().apply { addAll(NextObjectPrompts.round(seed + round * 13)) } }
    var swaps by remember { mutableIntStateOf(0) }
    val ripple = remember { Animatable(0f) }
    var rippleKey by remember { mutableIntStateOf(0) }
    var finished by remember(round) { mutableStateOf(false) }

    LaunchedEffect(rippleKey) {
        if (rippleKey > 0 && !env.reducedMotion) {
            ripple.snapTo(0f)
            ripple.animateTo(1f, tween(700))
        }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Beads(total = prompts.size, done = if (finished) prompts.size else index, modifier = Modifier.align(Alignment.CenterHorizontally))
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(260.dp)) {
                val p = ripple.value
                if (p > 0f && p < 1f) {
                    drawCircle(c.accent.copy(alpha = 0.35f * (1f - p)), radius = size.minDimension / 2 * (0.3f + 0.7f * p), style = Stroke(2.dp.toPx()))
                }
            }
            if (finished) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("That's a full round.", style = MaterialTheme.typography.headlineMedium, color = c.text)
                    if (!env.minimal) {
                        Spacer(Modifier.height(8.dp))
                        Text("You've been noticing real things around you.", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
                    }
                }
            } else {
                AnimatedContent(
                    targetState = prompts.getOrNull(index),
                    transitionSpec = {
                        if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                        else (fadeIn(tween(380)) + slideInVertically(tween(380)) { it / 8 }) togetherWith fadeOut(tween(160))
                    },
                    label = "prompt",
                ) { prompt ->
                    if (prompt != null) {
                        Column {
                            Text(
                                prompt.text,
                                style = if (env.simplified) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                                color = c.text,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                            )
                            if (prompt.hint.isNotEmpty() && !env.minimal) {
                                Spacer(Modifier.height(10.dp))
                                Text(prompt.hint, style = MaterialTheme.typography.bodyLarge, color = c.textDim)
                            }
                        }
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
            if (finished) {
                PrimaryButton("I'm done with this", onDone)
                SecondaryButton("Another round", { round++; index = 0 })
            } else {
                PrimaryButton("Found it", {
                    haptics.confirm()
                    rippleKey++
                    if (index + 1 >= prompts.size) finished = true else index++
                })
                if (index >= 3) {
                    GhostButton("Finish here", onDone, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                if (!env.minimal) {
                    GhostButton("A different prompt", {
                        val cur = prompts[index]
                        prompts[index] = NextObjectPrompts.alternative(cur, prompts, seed + swaps++)
                    }, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
