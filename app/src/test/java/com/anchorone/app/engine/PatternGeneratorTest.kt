package com.anchorone.app.engine

import com.anchorone.app.engine.PatternGenerator.Level
import com.anchorone.app.engine.PatternGenerator.Template
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class PatternGeneratorTest {

    @Test
    fun `complexity grows with level for every template`() {
        Template.entries.forEach { t ->
            val g = PatternGenerator.generate(t, Level.GENTLE).slots.size
            val s = PatternGenerator.generate(t, Level.STEADY).slots.size
            val d = PatternGenerator.generate(t, Level.DETAILED).slots.size
            assertTrue("$t gentle<steady", g < s)
            assertTrue("$t steady<detailed", s < d)
        }
    }

    @Test
    fun `pattern fits inside the unit circle`() {
        Template.entries.forEach { t -> Level.entries.forEach { l ->
            PatternGenerator.generate(t, l).slots.forEach { s ->
                assertTrue("$t/$l slot at ${s.x},${s.y}", hypot(s.x, s.y) + 0.0f <= 1.0f)
            }
        } }
    }

    @Test
    fun `every piece fits its own slot`() {
        Template.entries.forEach { t -> Level.entries.forEach { l ->
            val p = PatternGenerator.generate(t, l)
            p.slots.indices.forEach { assertTrue(p.fits(it, it)) }
        } }
    }

    @Test
    fun `gentle hex pieces are interchangeable, steady ones respect tone`() {
        val gentle = PatternGenerator.generate(Template.HEX_BLOOM, Level.GENTLE)
        // Rotations differ by 60deg but triangles have 120deg symmetry, so alternate slots match.
        assertTrue(gentle.fits(0, 2))
        val steady = PatternGenerator.generate(Template.HEX_BLOOM, Level.STEADY)
        assertFalse("different tones never fit", steady.fits(0, 1))
    }

    @Test
    fun `next rotates templates`() {
        assertEquals(Template.HEX_BLOOM, PatternGenerator.next(0, Level.GENTLE).template)
        assertEquals(Template.COMPASS, PatternGenerator.next(1, Level.GENTLE).template)
        assertEquals(Template.MOSAIC, PatternGenerator.next(2, Level.GENTLE).template)
        assertEquals(Template.HEX_BLOOM, PatternGenerator.next(-3, Level.GENTLE).template)
    }
}
