package com.anchorone.app.engine

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Pure geometry for Pattern Restore. Coordinates are in a unit space where the pattern
 * fits inside a circle of radius 1 around (0,0); the UI scales to the screen.
 */
object PatternGenerator {

    enum class Shape { TRIANGLE, DIAMOND, CIRCLE, SQUARE }

    enum class Level(val label: String) { GENTLE("Gentle"), STEADY("Steady"), DETAILED("Detailed") }

    enum class Template { HEX_BLOOM, COMPASS, MOSAIC }

    /**
     * A target position. [rotation] in degrees (direction of the first vertex, y-down screen space).
     * [size]: triangle = circumradius, diamond = half the long axis, circle = radius, square = side.
     * [tone] picks one of a few palette colours.
     */
    data class Slot(val x: Float, val y: Float, val shape: Shape, val rotation: Float, val tone: Int, val size: Float)

    data class Pattern(val template: Template, val level: Level, val slots: List<Slot>) {
        /** A piece made for slot [pieceIndex] fits [slotIndex] if they look identical. */
        fun fits(pieceIndex: Int, slotIndex: Int): Boolean {
            val a = slots[pieceIndex]
            val b = slots[slotIndex]
            return a.shape == b.shape && a.tone == b.tone && rotationsMatch(a, b)
        }

        private fun rotationsMatch(a: Slot, b: Slot): Boolean {
            val symmetry = when (a.shape) {
                Shape.CIRCLE -> return true
                Shape.SQUARE -> 90f
                Shape.DIAMOND -> 180f
                Shape.TRIANGLE -> 120f
            }
            val diff = ((a.rotation - b.rotation) % symmetry + symmetry) % symmetry
            return diff < 1f || symmetry - diff < 1f
        }
    }

    fun generate(template: Template, level: Level): Pattern {
        val slots = when (template) {
            Template.HEX_BLOOM -> hexBloom(level)
            Template.COMPASS -> compass(level)
            Template.MOSAIC -> mosaic(level)
        }
        return Pattern(template, level, slots)
    }

    /** Rotates through templates so consecutive puzzles differ. */
    fun next(index: Int, level: Level): Pattern =
        generate(Template.entries[Math.floorMod(index, Template.entries.size)], level)

    private fun polar(radius: Float, deg: Float): Pair<Float, Float> {
        val r = deg * PI / 180.0
        return (radius * cos(r)).toFloat() to (radius * sin(r)).toFloat()
    }

    private fun hexBloom(level: Level): List<Slot> {
        val out = mutableListOf<Slot>()
        // Six triangles forming a hexagon; each points outward/inward alternately.
        // Circumradius equal to the centroid distance makes the six triangles tile a hexagon.
        val triSize = 0.27f
        for (i in 0 until 6) {
            val angle = i * 60f + 30f
            val (x, y) = polar(0.28f, angle)
            val tone = when (level) {
                Level.GENTLE -> 0
                else -> i % 2
            }
            // Equilateral triangle with a vertex pointing toward the centre.
            out += Slot(x, y, Shape.TRIANGLE, angle + 180f, tone, triSize)
        }
        if (level != Level.GENTLE) out += Slot(0f, 0f, Shape.CIRCLE, 0f, 2, 0.16f)
        if (level == Level.DETAILED) {
            for (i in 0 until 6) {
                val angle = i * 60f
                val (x, y) = polar(0.72f, angle)
                out += Slot(x, y, Shape.DIAMOND, angle, 2, 0.26f)
            }
        }
        return out
    }

    private fun compass(level: Level): List<Slot> {
        val out = mutableListOf<Slot>()
        val count = if (level == Level.GENTLE) 4 else 8
        for (i in 0 until count) {
            val angle = i * (360f / count) - 90f
            val (x, y) = polar(0.5f, angle)
            val tone = if (level == Level.GENTLE) 0 else i % 2
            out += Slot(x, y, Shape.DIAMOND, angle, tone, if (count == 4) 0.34f else 0.27f)
        }
        out += Slot(0f, 0f, Shape.CIRCLE, 0f, 2, 0.2f)
        if (level == Level.DETAILED) {
            for (i in 0 until 4) {
                val angle = i * 90f - 45f
                val (x, y) = polar(0.86f, angle)
                out += Slot(x, y, Shape.CIRCLE, 0f, 1, 0.1f)
            }
        }
        return out
    }

    private fun mosaic(level: Level): List<Slot> {
        val n = when (level) {
            Level.GENTLE -> 2
            Level.STEADY -> 3
            Level.DETAILED -> 4
        }
        val cell = 1.3f / n
        val start = -0.65f + cell / 2
        val out = mutableListOf<Slot>()
        for (r in 0 until n) for (c in 0 until n) {
            val tone = when (level) {
                Level.GENTLE -> 0
                Level.STEADY -> (r + c) % 2
                Level.DETAILED -> if ((r == 0 || r == n - 1) && (c == 0 || c == n - 1)) 2 else (r + c) % 2
            }
            out += Slot(start + c * cell, start + r * cell, Shape.SQUARE, 0f, tone, cell * 0.86f)
        }
        return out
    }
}
