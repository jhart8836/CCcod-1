package com.anchorone.app.guidance

import com.anchorone.app.content.NextObjectPrompts
import com.anchorone.app.content.ReEntryContent
import com.anchorone.app.content.SingleThreadTasks
import com.anchorone.app.content.ThoughtContent
import com.anchorone.app.engine.Experience
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionTest {

    @Test
    fun `keep guiding never repeats a line back to back, across many cycles`() {
        Experience.entries.forEach { e ->
            val c = Companion(e, seed = 3)
            var last = ""
            repeat(80) {
                val u = c.keepGuiding()
                assertNotEquals("repeat for $e", last, u.text)
                last = u.text
            }
        }
    }

    @Test
    fun `a full pool is used before anything repeats`() {
        val c = Companion(Experience.THOUGHT_SPIRAL, seed = 11)
        val pool = GuidanceLibrary.lines(GuidanceLibrary.Kind.GUIDE, Experience.THOUGHT_SPIRAL).size
        val seen = (1..pool).map { c.keepGuiding().text }.toSet()
        assertEquals(pool, seen.size)
    }

    @Test
    fun `pathway-specific guidance is included`() {
        val c = Companion(Experience.DISCONNECTED, seed = 1)
        val specific = GuidanceLibrary.lines(GuidanceLibrary.Kind.GUIDE, Experience.DISCONNECTED).take(5).map { it.full }.toSet()
        val heard = (1..40).map { c.keepGuiding().text }.toSet()
        assertTrue(heard.any { it in specific })
    }

    @Test
    fun `something to do waits for done, then guiding resumes`() {
        val c = Companion(null, seed = 2)
        val task = c.somethingToDo()
        assertTrue(task.awaitingDone)
        assertEquals(Companion.Mode.TASK, c.mode)
        val ack = c.taskDone()
        assertFalse(ack.awaitingDone)
        assertEquals(Companion.Mode.GUIDING, c.mode)
    }

    @Test
    fun `less talking switches to short lines and longer gaps`() {
        val c = Companion(Experience.OVERWHELMED, seed = 5)
        val normalGap = c.gapMillis()
        c.lessTalking()
        assertEquals(Companion.Verbosity.LESS, c.verbosity)
        assertTrue(c.gapMillis() > normalGap)
        val shorts = GuidanceLibrary.lines(GuidanceLibrary.Kind.GUIDE, Experience.OVERWHELMED).map { it.short }.toSet()
        repeat(10) { assertTrue(c.keepGuiding().text in shorts) }
        c.moreTalking()
        assertEquals(Companion.Verbosity.FULL, c.verbosity)
    }

    @Test
    fun `ready closes the conversation`() {
        val c = Companion(null, seed = 9)
        c.open()
        val u = c.ready()
        assertEquals(Companion.Mode.CLOSING, u.mode)
    }

    @Test
    fun `short lines are actually shorter and nothing promises safety or forces breathing`() {
        val banned = listOf("you are safe", "you're safe", "breathe in", "breathe out", "deep breath", "everything is fine", "everything will be")
        GuidanceLibrary.allLines().forEach { line ->
            assertTrue("short not shorter: ${line.full}", line.short.length <= line.full.length)
            banned.forEach { b ->
                assertFalse("'$b' in: ${line.full}", line.full.lowercase().contains(b))
            }
        }
    }

    @Test
    fun `speech estimate is bounded`() {
        assertEquals(1_200L, Companion.estimatedSpeechMillis("Okay."))
        assertEquals(12_000L, Companion.estimatedSpeechMillis("word ".repeat(200)))
    }

    @Test
    fun `content libraries are well-formed`() {
        val round = NextObjectPrompts.round(4)
        assertEquals(7, round.size)
        assertEquals(listOf(1, 1, 1, 2, 2, 3, 3), round.map { it.level })
        assertEquals(round.size, round.toSet().size)
        val alt = NextObjectPrompts.alternative(round[0], round, 1)
        assertFalse(alt in round)

        SingleThreadTasks.threads.forEach { t ->
            assertEquals(SingleThreadTasks.Kind.CHOICE, t.last().kind)
            assertTrue(t.any { it.kind == SingleThreadTasks.Kind.HOLD })
        }
        ReEntryContent.defaults.forEach { assertEquals(3, it.steps.size) }
        assertTrue(ReEntryContent.custom("Walk the dog").steps[1].contains("Walk the dog"))
        assertEquals("I'm having the thought that I can't cope.", ThoughtContent.unhooked("I can't cope"))
        assertEquals("I notice I'm having the thought that people will notice.", ThoughtContent.noticed("People will notice"))
        assertEquals("I'm having the thought that something bad will happen.", ThoughtContent.unhooked("Something bad will happen!"))
        assertEquals("I'm having the thought that something difficult.", ThoughtContent.unhooked("  "))
    }
}
