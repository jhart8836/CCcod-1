package com.anchorone.app.ui.activities

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.anchorone.app.ui.theme.Anchor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

private class Touch(var x: Float, var y: Float, var dx: Float, var dy: Float, var age: Float)
private class Ripple(val x: Float, val y: Float, var age: Float)

/**
 * Particle flow simulation rendered into a half-resolution offscreen bitmap that fades a
 * little each frame — giving soft light trails at a fraction of full-screen cost.
 */
private class FlowSim(private val density: Float) {
    var w = 0
    var h = 0
    private var n = 0
    private var x = FloatArray(0)
    private var y = FloatArray(0)
    private var life = FloatArray(0)
    private var seed = 0x2545F491
    private val lines = FloatArray(4 * 900)
    val touches = ArrayList<Touch>()
    val ripples = ArrayList<Ripple>()
    var bitmap: Bitmap? = null
    private var canvas: android.graphics.Canvas? = null
    private val fade = Paint()
    private val paintA = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private val paintB = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; style = Paint.Style.STROKE }
    private var bg = 0
    private val scale = 0.5f

    private fun rnd(): Float {
        seed = seed xor (seed shl 13); seed = seed xor (seed ushr 17); seed = seed xor (seed shl 5)
        return (seed ushr 8) / 16777216f
    }

    fun ensure(width: Int, height: Int, bgColor: Color, a: Color, b: Color) {
        val bgArgb = bgColor.toArgb()
        if (width == w && height == h && bitmap != null && bgArgb == bg) return
        w = width; h = height; bg = bgArgb
        bitmap?.recycle()
        val bw = (w * scale).toInt().coerceAtLeast(1)
        val bh = (h * scale).toInt().coerceAtLeast(1)
        bitmap = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888).also { bmp ->
            canvas = android.graphics.Canvas(bmp).apply { drawColor(bgArgb) }
        }
        fade.color = bgArgb
        fade.alpha = 22
        paintA.color = a.copy(alpha = 0.75f).toArgb()
        paintB.color = b.copy(alpha = 0.65f).toArgb()
        paintA.strokeWidth = 1.3f * density * scale
        paintB.strokeWidth = 1.1f * density * scale
        val areaDp = (w / density) * (h / density)
        n = (areaDp / 300f).toInt().coerceIn(220, 900)
        x = FloatArray(n) { rnd() * w }
        y = FloatArray(n) { rnd() * h }
        life = FloatArray(n) { rnd() * 6f }
    }

    fun step(dt: Float, t: Float, speedScale: Float) {
        val c = canvas ?: return
        val base = 38f * density * speedScale
        val radius = 130f * density
        val r2 = radius * radius
        touches.removeAll { it.age > 3.5f }
        touches.forEach { it.age += dt }
        ripples.removeAll { it.age > 1.6f }
        ripples.forEach { it.age += dt }
        var ia = 0
        val half = n / 2
        for (i in 0 until n) {
            if (i == half && ia > 0) {
                c.drawLines(lines, 0, ia, paintA)
                ia = 0
            }
            val px = x[i]
            val py = y[i]
            val angle = (sin(px * 0.0032f / density + t * 0.13f) + cos(py * 0.0041f / density - t * 0.09f) +
                sin((px + py) * 0.0017f / density + t * 0.05f)) * PI.toFloat()
            var vx = cos(angle) * base
            var vy = sin(angle) * base
            for (tc in touches) {
                val dx = px - tc.x
                val dy = py - tc.y
                val d2 = dx * dx + dy * dy
                if (d2 > r2 * 4f) continue
                val strength = exp(-tc.age * 1.1f)
                val fall = exp(-d2 / r2) * strength
                val d = sqrt(d2) + 1f
                vx += (-dy / d) * 260f * density * fall + tc.dx * 5f * fall
                vy += (dx / d) * 260f * density * fall + tc.dy * 5f * fall
            }
            val nx = px + vx * dt
            val ny = py + vy * dt
            life[i] -= dt
            if (life[i] <= 0f || nx < -8 || ny < -8 || nx > w + 8 || ny > h + 8) {
                x[i] = rnd() * w; y[i] = rnd() * h; life[i] = 3f + rnd() * 5f
                continue
            }
            x[i] = nx; y[i] = ny
            if (ia + 4 <= lines.size) {
                lines[ia] = px * scale; lines[ia + 1] = py * scale; lines[ia + 2] = nx * scale; lines[ia + 3] = ny * scale
                ia += 4
            }
        }
        if (ia > 0) c.drawLines(lines, 0, ia, paintB)
        c.drawRect(0f, 0f, c.width.toFloat(), c.height.toFloat(), fade)
    }

    fun release() {
        bitmap?.recycle()
        bitmap = null
        canvas = null
    }
}

@Composable
fun FlowFieldActivity(paused: Boolean, onDone: () -> Unit) {
    val env = Anchor.env
    if (env.reducedMotion) {
        StillFlowField(onDone)
        return
    }
    val c = Anchor.colors
    val density = LocalDensity.current.density
    val sim = remember { FlowSim(density) }
    DisposableEffect(Unit) { onDispose { sim.release() } }

    val frame = rememberFrameLoop(!paused) { dt, t -> sim.step(dt, t, env.motionScale) }

    ActivityFrame(
        instruction = "Move your finger through the light.",
        sub = "Swirl it, push it, let it settle. There's nothing to get right.",
        onDone = onDone,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Flowing light. Drag or tap to shape it." }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { sim.touches.add(Touch(it.x, it.y, 0f, 0f, 0f)) },
                        onDrag = { change, amount ->
                            val p = change.position
                            val last = sim.touches.lastOrNull()
                            if (last == null || (Offset(last.x, last.y) - p).getDistance() > 30f * density) {
                                if (sim.touches.size > 10) sim.touches.removeAt(0)
                                sim.touches.add(Touch(p.x, p.y, amount.x, amount.y, 0f))
                            } else {
                                last.x = p.x; last.y = p.y; last.dx = amount.x; last.dy = amount.y; last.age = 0f
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        sim.ripples.add(Ripple(p.x, p.y, 0f))
                        sim.touches.add(Touch(p.x, p.y, 0f, 0f, 0f))
                    }
                },
        ) {
            frame.value
            sim.ensure(size.width.toInt(), size.height.toInt(), c.bgBottom, c.accent, c.teal)
            sim.bitmap?.let { bmp ->
                drawImage(
                    bmp.asImageBitmap(),
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(bmp.width, bmp.height),
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                    filterQuality = FilterQuality.Low,
                )
            }
            sim.ripples.forEach { r ->
                val p = r.age / 1.6f
                drawCircle(c.accent.copy(alpha = 0.4f * (1f - p)), radius = (24f + 140f * p) * density, center = Offset(r.x, r.y), style = Stroke(1.5f * density))
            }
        }
    }
}

/** Reduced-motion alternative: a still field of light lines; taps place "stones" the stream bends around. */
@Composable
private fun StillFlowField(onDone: () -> Unit) {
    val c = Anchor.colors
    val stones = remember { mutableListOf<Offset>() }
    var version by remember { mutableIntStateOf(0) }
    ActivityFrame(
        instruction = "Tap to place a stone in the stream.",
        sub = "Still version — nothing moves on its own.",
        onDone = onDone,
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Still flow field. Tap to place stones." }
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        if (stones.size >= 8) stones.removeAt(0)
                        stones.add(p)
                        version++
                    }
                },
        ) {
            version
            val step = 26.dp.toPx()
            val len = 11.dp.toPx()
            val radius = 110.dp.toPx()
            var gy = step / 2
            while (gy < size.height) {
                var gx = step / 2
                while (gx < size.width) {
                    var angle = (sin(gx * 0.004f) + cos(gy * 0.005f) + sin((gx + gy) * 0.002f)) * PI.toFloat()
                    var vx = cos(angle)
                    var vy = sin(angle)
                    var glow = 0f
                    for (s in stones) {
                        val dx = gx - s.x
                        val dy = gy - s.y
                        val d2 = dx * dx + dy * dy
                        val fall = exp(-d2 / (radius * radius))
                        val d = sqrt(d2) + 1f
                        vx += (-dy / d) * 2.2f * fall
                        vy += (dx / d) * 2.2f * fall
                        glow = maxOf(glow, fall)
                    }
                    angle = kotlin.math.atan2(vy, vx)
                    val ex = cos(angle) * len
                    val ey = sin(angle) * len
                    drawLine(
                        (if ((gx / step).toInt() % 2 == 0) c.accent else c.teal).copy(alpha = 0.25f + 0.5f * glow),
                        Offset(gx - ex / 2, gy - ey / 2), Offset(gx + ex / 2, gy + ey / 2),
                        strokeWidth = 1.6.dp.toPx(),
                    )
                    gx += step
                }
                gy += step
            }
            stones.forEach { s ->
                for (k in 1..3) drawCircle(c.accent.copy(alpha = 0.3f / k), radius = 14.dp.toPx() * k, center = s, style = Stroke(1.5.dp.toPx()))
                drawCircle(c.silver.copy(alpha = 0.8f), radius = 6.dp.toPx(), center = s)
            }
        }
    }
}
