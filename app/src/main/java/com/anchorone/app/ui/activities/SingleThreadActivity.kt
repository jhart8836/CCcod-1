package com.anchorone.app.ui.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anchorone.app.content.SingleThreadTasks
import com.anchorone.app.content.SingleThreadTasks.Kind
import com.anchorone.app.ui.components.Beads
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * SINGLE THREAD — one tiny concrete task at a time, from simple observation toward
 * practical engagement. Each task can be swapped for an easier alternative.
 */
@Composable
fun SingleThreadActivity(seed: Int, onDone: () -> Unit, onMoveOn: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    var threadOffset by rememberSaveable { mutableIntStateOf(0) }
    val thread = remember(threadOffset) { SingleThreadTasks.thread(seed + threadOffset) }
    var index by rememberSaveable(threadOffset) { mutableIntStateOf(0) }
    var useAlt by rememberSaveable(threadOffset, index) { mutableStateOf(false) }
    var resting by rememberSaveable { mutableStateOf(false) }
    val task = thread[index.coerceIn(0, thread.lastIndex)]
    val text = if (useAlt && task.alternative != null) task.alternative else task.text

    fun advance() {
        haptics.confirm()
        if (index < thread.lastIndex) index++ else onDone()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        Spacer(Modifier.height(8.dp))
        Beads(total = thread.size, done = index, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
        AnimatedContent(
            targetState = Triple(threadOffset, index, resting),
            transitionSpec = {
                if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(360)) togetherWith fadeOut(tween(160))
            },
            label = "task",
            modifier = Modifier.weight(1f),
        ) { (_, _, isResting) ->
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (isResting) {
                    Text("Stay here for as long as you like.", style = MaterialTheme.typography.headlineLarge, color = c.text)
                    Text("Tap when you're ready for what's next.", style = MaterialTheme.typography.bodyLarge, color = c.textDim)
                    Spacer(Modifier.weight(1f))
                    PrimaryButton("Ready", { resting = false; onDone() })
                } else {
                    Text(
                        text,
                        style = if (env.simplified) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                        color = c.text,
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        when (task.kind) {
                            Kind.HOLD -> HoldCircle(onComplete = { advance() })
                            Kind.SLIDE -> SlideToContinue(onComplete = { advance() })
                            Kind.CHOICE -> Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                task.options.forEachIndexed { i, opt ->
                                    ChoiceCard(opt, null, {
                                        haptics.confirm()
                                        when (i) {
                                            0 -> resting = true
                                            1 -> onDone()
                                            2 -> onMoveOn()
                                            else -> { threadOffset++; index = 0 }
                                        }
                                    })
                                }
                            }
                            Kind.TAP -> Unit
                        }
                    }
                    if (task.kind == Kind.TAP) {
                        PrimaryButton("Done", { advance() })
                    }
                    if (task.alternative != null && !useAlt && task.kind != Kind.CHOICE) {
                        GhostButton("Can't right now", { useAlt = true }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else if (task.kind == Kind.TAP && !env.minimal) {
                        GhostButton("Skip this one", { advance() }, modifier = Modifier.align(Alignment.CenterHorizontally))
                    } else {
                        Spacer(Modifier.height(52.dp))
                    }
                }
            }
        }
    }
}

/** Press and hold: a ring fills over ~2 seconds. Letting go simply eases it back — never a failure. */
@Composable
fun HoldCircle(onComplete: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    var completed by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()

    Box(
        Modifier
            .size(200.dp)
            .semantics {
                contentDescription = "Hold circle. Press and hold until it fills."
                role = Role.Button
                // Accessibility: a single activation completes it for screen-reader users.
                onClick { if (!completed) { completed = true; onComplete() }; true }
            }
            .pointerInput(Unit) {
                detectTapGestures(onPress = {
                    if (completed) return@detectTapGestures
                    job?.cancel()
                    job = scope.launch {
                        val remaining = ((1f - progress.value) * 2200).toInt().coerceAtLeast(1)
                        progress.animateTo(1f, tween(remaining, easing = LinearEasing))
                        if (!completed) {
                            completed = true
                            haptics.confirm()
                            onComplete()
                        }
                    }
                    tryAwaitRelease()
                    if (!completed) {
                        job?.cancel()
                        job = scope.launch { progress.animateTo(0f, tween(if (env.reducedMotion) 0 else 900)) }
                    }
                })
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 10.dp.toPx()
            val inset = stroke / 2
            drawCircle(c.accentSoft, radius = size.minDimension / 2 - inset, style = Stroke(stroke))
            drawArc(
                c.accent, startAngle = -90f, sweepAngle = 360f * progress.value, useCenter = false,
                topLeft = Offset(inset, inset), size = Size(size.width - stroke, size.height - stroke),
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            drawCircle(c.accent.copy(alpha = 0.10f + 0.25f * progress.value), radius = size.minDimension / 2 * 0.62f)
        }
        Text("Hold", style = MaterialTheme.typography.titleLarge, color = c.text)
    }
}

/** Slide the thumb to the end of the track. */
@Composable
fun SlideToContinue(onComplete: () -> Unit, label: String = "Slide") {
    val c = Anchor.colors
    val haptics = rememberHaptics()
    var offset by remember { mutableFloatStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    val thumb = 64.dp
    val thumbPx = with(LocalDensity.current) { thumb.toPx() }
    BoxWithConstraints(
        Modifier
            .fillMaxWidth()
            .height(76.dp)
            .clip(RoundedCornerShape(38.dp))
            .background(c.surface)
            .semantics {
                contentDescription = "Slide to continue"
                role = Role.Button
                onClick { if (!completed) { completed = true; onComplete() }; true }
            },
    ) {
        val maxPx = constraints.maxWidth - thumbPx - 12f
        Text(
            "$label  →",
            style = MaterialTheme.typography.titleMedium,
            color = c.textDim,
            modifier = Modifier.align(Alignment.Center),
        )
        Box(
            Modifier
                .padding(6.dp)
                .offset { IntOffset(offset.roundToInt(), 0) }
                .size(thumb)
                .clip(CircleShape)
                .background(c.accent)
                .pointerInput(maxPx) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offset > maxPx * 0.85f && !completed) {
                                completed = true
                                offset = maxPx
                                haptics.confirm()
                                onComplete()
                            } else if (!completed) offset = 0f
                        },
                    ) { change, amount ->
                        change.consume()
                        offset = (offset + amount).coerceIn(0f, maxPx)
                    }
                },
        )
    }
}
