package com.anchorone.app.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * ORBIT — a luminous object with spring physics that follows the finger, glides with
 * inertia, and softly lights up guide shapes when traced. No score, no timer, no failure.
 */
private class OrbitSim {
    var w = 0f
    var h = 0f
    var pos = Offset.Zero
    var vel = Offset.Zero
    var finger: Offset? = null
    val trail = ArrayDeque<Offset>()
    var guide: List<Offset> = emptyList()
    var lit = FloatArray(0)
    var everLit = BooleanArray(0)
    var lastLitIndex = -1
    var litSteps = 0
    var completeGlow = 0f
    var shape = 0
    var initialised = false

    fun layout(width: Float, height: Float, shapeIndex: Int) {
        if (width == w && height == h && shapeIndex == shape && initialised) return
        w = width; h = height; shape = shapeIndex
        if (!initialised) {
            pos = Offset(w / 2, h * 0.5f)
            initialised = true
        }
        val cx = w / 2
        val cy = h / 2
        val r = min(w, h) * 0.33f
        val n = 140
        guide = when (shapeIndex) {
            1 -> List(n) { i ->
                val t = i / n.toFloat() * 2 * PI
                Offset(cx + r * cos(t).toFloat(), cy + r * sin(t).toFloat())
            }
            2 -> List(n) { i ->
                // Lemniscate of Gerono — a soft figure-eight.
                val t = i / n.toFloat() * 2 * PI
                Offset(cx + r * 1.15f * sin(t).toFloat(), cy + r * 1.1f * (sin(t) * cos(t)).toFloat() * 1.6f)
            }
            3 -> List(n) { i ->
                // Rounded wave across the screen.
                val t = i / (n - 1f)
                Offset(w * 0.12f + t * w * 0.76f, cy + (sin(t * 3 * PI) * r * 0.55f).toFloat())
            }
            else -> emptyList()
        }
        lit = FloatArray(guide.size)
        everLit = BooleanArray(guide.size)
        lastLitIndex = -1
        litSteps = 0
        completeGlow = 0f
    }

    /** Returns true when an audible/haptic "tick" should play. */
    fun step(dt: Float, reduced: Boolean, motionScale: Float, near: Float): Pair<Boolean, Boolean> {
        val f = finger
        if (reduced) {
            if (f != null) pos = f
            vel = Offset.Zero
        } else if (f != null) {
            val k = 70f
            val d = 13f
            val acc = (f - pos) * k - vel * d
            vel += acc * dt
        } else {
            vel *= exp(-1.1f * dt)
            // Gentle pull: toward the nearest guide point when tracing, else toward centre.
            val target = nearestGuide()?.second ?: Offset(w / 2, h / 2)
            val pull = if (guide.isEmpty()) 0.5f else 1.4f
            vel += (target - pos) * (pull * dt) * motionScale
        }
        val speed = vel.getDistance()
        val maxSpeed = 2600f
        if (speed > maxSpeed) vel *= maxSpeed / speed
        pos += vel * dt
        // Soft edges.
        val m = 18f
        if (pos.x < m) { pos = pos.copy(x = m); vel = vel.copy(x = -vel.x * 0.5f) }
        if (pos.x > w - m) { pos = pos.copy(x = w - m); vel = vel.copy(x = -vel.x * 0.5f) }
        if (pos.y < m) { pos = pos.copy(y = m); vel = vel.copy(y = -vel.y * 0.5f) }
        if (pos.y > h - m) { pos = pos.copy(y = h - m); vel = vel.copy(y = -vel.y * 0.5f) }

        if (!reduced) {
            trail.addLast(pos)
            while (trail.size > 34) trail.removeFirst()
        } else trail.clear()

        var tick = false
        var completed = false
        for (i in lit.indices) lit[i] = (lit[i] - dt * 0.18f).coerceAtLeast(0f)
        val nearest = nearestGuide()
        if (nearest != null && f != null) {
            val (idx, p) = nearest
            if ((p - pos).getDistance() < near) {
                for (j in -2..2) {
                    val k = Math.floorMod(idx + j, lit.size)
                    lit[k] = 1f
                    everLit[k] = true
                }
                if (idx != lastLitIndex) {
                    litSteps++
                    if (litSteps % 7 == 0) tick = true
                    lastLitIndex = idx
                }
                if (completeGlow <= 0f && everLit.all { it }) {
                    completeGlow = 1f
                    completed = true
                }
            }
        }
        if (completeGlow > 0f) completeGlow = (completeGlow - dt * 0.35f).coerceAtLeast(0f)
        return tick to completed
    }

    private fun nearestGuide(): Pair<Int, Offset>? {
        if (guide.isEmpty()) return null
        var best = 0
        var bestD = Float.MAX_VALUE
        for (i in guide.indices) {
            val g = guide[i]
            val d = hypot(g.x - pos.x, g.y - pos.y)
            if (d < bestD) { bestD = d; best = i }
        }
        return best to guide[best]
    }
}

private val shapeNames = listOf("Free", "Ring", "Figure-eight", "Wave")
private val shapePrompts = listOf(
    "Drag the light slowly. It follows your finger.",
    "Trace the ring with the light.",
    "Follow the figure-eight. Change direction whenever you like.",
    "Glide along the wave, left to right and back.",
)

@Composable
fun OrbitActivity(paused: Boolean, onDone: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    val sim = remember { OrbitSim() }
    var shape by remember { mutableIntStateOf(0) }
    var completions by remember { mutableIntStateOf(0) }
    val near = with(LocalDensity.current) { 30.dp.toPx() }
    val orbR = with(LocalDensity.current) { 22.dp.toPx() }

    val frame = rememberFrameLoop(!paused) { dt, _ ->
        val (tick, completed) = sim.step(dt, env.reducedMotion, env.motionScale.coerceAtLeast(0.4f), near)
        if (tick) haptics.tick()
        if (completed) { haptics.confirm(); completions++ }
    }

    val prompt = if (completions > 0 && shape != 0) "That's the whole shape. Try another, or keep moving freely." else shapePrompts[shape]

    ActivityFrame(instruction = prompt, onDone = onDone, sub = if (shape == 0) "Change direction whenever you like. There's nothing to get right." else null) {
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Orbit play area. Drag to move the light." }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { sim.finger = it },
                            onDragEnd = { sim.finger = null },
                            onDragCancel = { sim.finger = null },
                            onDrag = { change, _ -> sim.finger = change.position },
                        )
                    }
                    .pointerInput(Unit) {
                        // A tap nudges the light toward the touch point.
                        awaitPointerEventScope {
                            while (true) {
                                val e = awaitPointerEvent()
                                val p = e.changes.firstOrNull() ?: continue
                                if (p.changedToDown() && sim.finger == null) {
                                    val dir = p.position - sim.pos
                                    sim.vel += dir * 2.2f
                                }
                            }
                        }
                    },
            ) {
                frame.value
                sim.layout(size.width, size.height, shape)

                // Guide path.
                if (sim.guide.isNotEmpty()) {
                    val path = Path()
                    sim.guide.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
                    if (shape == 1 || shape == 2) path.close()
                    drawPath(path, c.outline, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round))
                    for (i in sim.guide.indices) {
                        val l = sim.lit[i]
                        if (l > 0.02f) drawCircle(c.accent.copy(alpha = 0.55f * l), radius = 3.5.dp.toPx(), center = sim.guide[i])
                        else if (sim.everLit[i]) drawCircle(c.teal.copy(alpha = 0.22f), radius = 2.dp.toPx(), center = sim.guide[i])
                    }
                    if (sim.completeGlow > 0f) {
                        drawPath(path, c.accent.copy(alpha = 0.5f * sim.completeGlow), style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round))
                    }
                }

                // Trail.
                val n = sim.trail.size
                sim.trail.forEachIndexed { i, p ->
                    val a = (i + 1f) / n
                    drawCircle(c.accent.copy(alpha = 0.16f * a), radius = orbR * (0.35f + 0.6f * a), center = p)
                }

                // The orb.
                if (c.glow.alpha > 0f) {
                    drawCircle(
                        Brush.radialGradient(listOf(c.glow.copy(alpha = 0.35f), Color.Transparent), sim.pos, orbR * 3.4f),
                        radius = orbR * 3.4f, center = sim.pos,
                    )
                }
                drawCircle(
                    Brush.radialGradient(listOf(Color.White.copy(alpha = 0.95f), c.accent, c.teal.copy(alpha = 0.8f)), sim.pos - Offset(orbR * 0.3f, orbR * 0.3f), orbR * 1.3f),
                    radius = orbR, center = sim.pos,
                )
            }
            if (!env.minimal) {
                Row(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    shapeNames.forEachIndexed { i, name ->
                        Chip(name, shape == i, { shape = i; completions = 0 })
                    }
                }
            }
        }
    }
}
