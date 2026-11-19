package com.anchorone.app.ui.activities

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anchorone.app.engine.PatternGenerator
import com.anchorone.app.engine.PatternGenerator.Shape
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Mutable per-piece state, mutated inside the frame loop / gesture handlers. */
private class PieceState(val slot: Int) {
    var pos = Offset.Zero
    var target = Offset.Zero
    var placedAt: Int? = null
    var dragging = false
}

private class PatternBoard(val pattern: PatternGenerator.Pattern, seed: Int) {
    val pieces = pattern.slots.indices.map { PieceState(it) }.shuffled(java.util.Random(seed.toLong()))
    var w = 0f
    var h = 0f
    var center = Offset.Zero
    var scale = 1f
    var trayScale = 1f
    var laidOut = false
    var completeT = 0f

    fun slotCenter(i: Int): Offset {
        val s = pattern.slots[i]
        return center + Offset(s.x * scale, s.y * scale)
    }

    fun layout(width: Float, height: Float) {
        if (laidOut && width == w && height == h) return
        w = width; h = height
        val boardH = h * 0.62f
        scale = min(w, boardH) * 0.42f
        center = Offset(w / 2, boardH / 2 + 8f)
        // Tray: pieces arranged in rows below the board.
        val n = pieces.size
        val cols = when {
            n <= 4 -> n
            n <= 9 -> (n + 1) / 2
            else -> (n + 2) / 3
        }.coerceAtLeast(1)
        val rows = (n + cols - 1) / cols
        val trayTop = boardH + 12f
        val trayH = h - trayTop
        val cellW = w / cols
        val cellH = trayH / rows
        val maxPiece = pattern.slots.maxOf { pieceExtent(it) } * scale
        trayScale = (min(cellW, cellH) * 0.42f / maxPiece).coerceIn(0.45f, 1f)
        pieces.forEachIndexed { idx, p ->
            val col = idx % cols
            val row = idx / cols
            val home = Offset(cellW * (col + 0.5f), trayTop + cellH * (row + 0.5f))
            if (p.placedAt == null) {
                if (!laidOut) p.pos = home
                p.target = home
            } else {
                p.target = slotCenter(p.placedAt!!)
                if (!laidOut) p.pos = p.target
            }
        }
        laidOut = true
    }

    fun homeOf(p: PieceState): Offset {
        // Recompute tray position for this piece.
        val idx = pieces.indexOf(p)
        val n = pieces.size
        val cols = when {
            n <= 4 -> n
            n <= 9 -> (n + 1) / 2
            else -> (n + 2) / 3
        }.coerceAtLeast(1)
        val rows = (n + cols - 1) / cols
        val boardH = h * 0.62f
        val trayTop = boardH + 12f
        val cellW = w / cols
        val cellH = (h - trayTop) / rows
        return Offset(cellW * (idx % cols + 0.5f), trayTop + cellH * (idx / cols + 0.5f))
    }

    val complete: Boolean get() = pieces.all { it.placedAt != null }

    /** Eases pieces toward their targets. Returns true while anything is still moving. */
    fun step(dt: Float, instant: Boolean): Boolean {
        var moving = false
        pieces.forEach { p ->
            if (p.dragging) { moving = true; return@forEach }
            val d = p.target - p.pos
            if (d.getDistance() > 0.5f) {
                p.pos = if (instant) p.target else p.pos + d * (1f - kotlin.math.exp(-14f * dt))
                moving = true
            } else p.pos = p.target
        }
        if (complete && completeT < 1f) {
            completeT = (completeT + dt * 0.6f).coerceAtMost(1f)
            moving = true
        }
        return moving
    }
}

fun pieceExtent(s: PatternGenerator.Slot): Float = when (s.shape) {
    Shape.SQUARE -> s.size * 0.71f
    else -> s.size
}

private fun DrawScope.drawPiece(
    shape: Shape, center: Offset, rotationDeg: Float, sizePx: Float, color: Color, filled: Boolean, strokeW: Float,
) {
    val style = if (filled) Fill else Stroke(strokeW)
    when (shape) {
        Shape.CIRCLE -> drawCircle(color, radius = sizePx, center = center, style = style)
        Shape.SQUARE -> rotate(rotationDeg, center) {
            drawRoundRect(
                color, topLeft = center - Offset(sizePx / 2, sizePx / 2), size = Size(sizePx, sizePx),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(sizePx * 0.12f), style = style,
            )
        }
        Shape.TRIANGLE, Shape.DIAMOND -> {
            val path = Path()
            val pts = if (shape == Shape.TRIANGLE) {
                listOf(0f, 120f, 240f).map { a -> polar(center, sizePx, rotationDeg + a) }
            } else {
                listOf(
                    polar(center, sizePx, rotationDeg), polar(center, sizePx * 0.5f, rotationDeg + 90f),
                    polar(center, sizePx, rotationDeg + 180f), polar(center, sizePx * 0.5f, rotationDeg + 270f),
                )
            }
            pts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y) }
            path.close()
            drawPath(path, color, style = style)
        }
    }
}

private fun polar(c: Offset, r: Float, deg: Float): Offset {
    val a = deg * PI / 180.0
    return Offset(c.x + (r * cos(a)).toFloat(), c.y + (r * sin(a)).toFloat())
}

@Composable
fun PatternRestoreActivity(
    paused: Boolean,
    level: PatternGenerator.Level,
    seed: Int,
    onDone: () -> Unit,
    onLevel: (PatternGenerator.Level) -> Unit,
) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    var round by remember { mutableIntStateOf(0) }
    val board = remember(round, level) { PatternBoard(PatternGenerator.next(seed + round, level), seed * 31 + round) }
    var animating by remember(board) { mutableStateOf(true) }
    var done by remember(board) { mutableStateOf(false) }

    val frame = rememberFrameLoop(!paused && animating) { dt, _ ->
        val moving = board.step(dt, env.reducedMotion)
        if (!moving) animating = false
    }

    val instruction = when {
        done -> "Pattern restored."
        else -> "Drag each piece into a matching outline."
    }

    ActivityFrame(
        instruction = instruction,
        onDone = onDone,
        sub = if (done) "Take another, or finish here." else "No rush, no wrong moves. Pieces only settle where they fit.",
    ) {
        Column(Modifier.fillMaxSize()) {
            if (!env.minimal) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                ) {
                    PatternGenerator.Level.entries.forEach { l ->
                        Chip(l.label, l == level, { if (l != level) onLevel(l) })
                    }
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .semantics { contentDescription = "Pattern board. Drag pieces from the tray into the outlines." }
                        .pointerInput(board) {
                            var active: PieceState? = null
                            detectDragGestures(
                                onDragStart = { start ->
                                    active = board.pieces.lastOrNull { p ->
                                        p.placedAt == null &&
                                            (p.pos - start).getDistance() < pieceExtent(board.pattern.slots[p.slot]) * board.scale * 1.25f + 24f
                                    }
                                    active?.let { it.dragging = true; animating = true }
                                },
                                onDrag = { change, amount ->
                                    active?.let {
                                        it.pos += amount
                                        it.target = it.pos
                                        change.consume()
                                    }
                                    animating = true
                                },
                                onDragEnd = {
                                    active?.let { piece ->
                                        piece.dragging = false
                                        val snap = board.scale * 0.32f
                                        val slot = board.pattern.slots.indices
                                            .filter { s -> board.pieces.none { it.placedAt == s } && board.pattern.fits(piece.slot, s) }
                                            .minByOrNull { (board.slotCenter(it) - piece.pos).getDistance() }
                                            ?.takeIf { (board.slotCenter(it) - piece.pos).getDistance() < snap }
                                        if (slot != null) {
                                            piece.placedAt = slot
                                            piece.target = board.slotCenter(slot)
                                            haptics.confirm()
                                            if (board.complete) done = true
                                        } else {
                                            piece.target = board.homeOf(piece)
                                        }
                                    }
                                    active = null
                                    animating = true
                                },
                                onDragCancel = {
                                    active?.let { it.dragging = false; it.target = board.homeOf(it) }
                                    active = null
                                    animating = true
                                },
                            )
                        },
                ) {
                    frame.value
                    board.layout(size.width, size.height)
                    val tones = c.tones
                    val stroke = 1.5.dp.toPx()
                    val spin = if (env.reducedMotion) 0f else board.completeT * 30f * env.motionScale

                    if (done && c.glow.alpha > 0f) {
                        drawCircle(
                            Brush.radialGradient(listOf(c.glow.copy(alpha = 0.25f * board.completeT), Color.Transparent), board.center, board.scale * 1.3f),
                            radius = board.scale * 1.3f, center = board.center,
                        )
                    }
                    rotate(spin, board.center) {
                        board.pattern.slots.forEachIndexed { i, s ->
                            val filled = board.pieces.any { it.placedAt == i && (it.pos - it.target).getDistance() < 1f }
                            if (!filled) {
                                drawPiece(s.shape, board.slotCenter(i), s.rotation, s.size * board.scale, tones[s.tone].copy(alpha = 0.35f), false, stroke)
                            }
                        }
                    }
                    // Placed and settled pieces rotate with the board; moving ones draw above.
                    board.pieces.sortedBy { if (it.dragging) 2 else if (it.placedAt != null) 0 else 1 }.forEach { p ->
                        val s = board.pattern.slots[p.slot]
                        val settled = p.placedAt != null && (p.pos - p.target).getDistance() < 1f
                        val scale = if (p.placedAt != null || p.dragging) 1f else board.trayScale
                        val color = tones[s.tone].copy(alpha = if (settled) 0.9f else 0.8f)
                        if (settled) {
                            rotate(spin, board.center) {
                                drawPiece(s.shape, p.pos, s.rotation, s.size * board.scale, color, true, stroke)
                            }
                        } else {
                            drawPiece(s.shape, p.pos, s.rotation, s.size * board.scale * scale, color, true, stroke)
                        }
                    }
                }
                if (done) {
                    Row(Modifier.align(Alignment.BottomCenter).padding(8.dp)) {
                        GhostButton("Another pattern", { round++ }, color = c.accent)
                    }
                }
            }
        }
    }
}
