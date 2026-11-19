package com.anchorone.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionEngineTest {

    private val ctx = EngineContext()
    private fun SessionState.on(e: SessionEvent, c: EngineContext = ctx) = AnchorCore.reduce(this, e, c)

    @Test
    fun `labelled pathways open with a minimal-thought immediate action`() {
        listOf(Experience.PHYSICAL_ALARM, Experience.THOUGHT_SPIRAL, Experience.DISCONNECTED, Experience.OVERWHELMED).forEach { e ->
            val s = AnchorCore.start(e, ctx, 1L)
            assertEquals(Phase.IMMEDIATE, s.phase)
            assertNotNull(s.immediate)
            assertNull(s.activity)
        }
    }

    @Test
    fun `just help me start goes straight to a low-demand interactive exercise`() {
        val s = AnchorCore.start(Experience.JUST_START, ctx, 1L)
        assertEquals(Phase.STABILIZE, s.phase)
        assertEquals(1, s.activity!!.demand)
        assertEquals(Category.INTERACTIVE, s.activity!!.category)
    }

    @Test
    fun `full pathway walk - immediate, stabilise, checkpoint, follow-up, rating, transition, end`() {
        var s = AnchorCore.start(Experience.THOUGHT_SPIRAL, ctx, 1L)
        s = s.on(SessionEvent.CompleteImmediate)
        assertEquals(Phase.STABILIZE, s.phase)
        assertEquals(ActivityId.THOUGHT_UNHOOKING, s.activity)
        s = s.on(SessionEvent.CompleteActivity)
        assertEquals(Phase.CHECKPOINT, s.phase)
        assertEquals(listOf(ActivityId.THOUGHT_UNHOOKING), s.completed)
        s = s.on(SessionEvent.Continue)
        assertEquals(Phase.FOLLOW_UP, s.phase)
        assertTrue(s.activity in ActivityCatalog.pathway(Experience.THOUGHT_SPIRAL).followUp)
        assertNotEquals(ActivityId.THOUGHT_UNHOOKING, s.activity)
        s = s.on(SessionEvent.CompleteActivity)
        assertEquals(Phase.CHECKPOINT, s.phase)
        s = s.on(SessionEvent.ReadyToMoveOn)
        assertEquals(Phase.RATING, s.phase)
        s = s.on(SessionEvent.FinishRating)
        assertEquals(Phase.TRANSITION, s.phase)
        s = s.on(SessionEvent.Finish)
        assertTrue(s.isOver)
    }

    @Test
    fun `skipping the immediate step is always allowed`() {
        val s = AnchorCore.start(Experience.DISCONNECTED, ctx, 1L).on(SessionEvent.Skip)
        assertEquals(Phase.STABILIZE, s.phase)
        assertNotNull(s.activity)
    }

    @Test
    fun `skipping an activity offers a different one and remembers the skip`() {
        var s = AnchorCore.start(Experience.PHYSICAL_ALARM, ctx, 1L).on(SessionEvent.CompleteImmediate)
        val first = s.activity
        s = s.on(SessionEvent.Skip)
        assertNotEquals(first, s.activity)
        assertTrue(first in s.skipped)
    }

    @Test
    fun `skipping through a whole phase lands on the checkpoint, never a dead end`() {
        var s = AnchorCore.start(Experience.OVERWHELMED, ctx, 1L).on(SessionEvent.CompleteImmediate)
        repeat(10) { if (s.phase == Phase.STABILIZE) s = s.on(SessionEvent.Skip) }
        assertEquals(Phase.CHECKPOINT, s.phase)
        // And the person can still continue from there.
        val next = s.on(SessionEvent.Continue)
        assertEquals(Phase.FOLLOW_UP, next.phase)
        assertNotNull(next.activity)
    }

    @Test
    fun `something different works from every active phase`() {
        val base = AnchorCore.start(Experience.DISCONNECTED, ctx, 1L)
        val fromImmediate = base.on(SessionEvent.SomethingDifferent)
        assertEquals(Phase.STABILIZE, fromImmediate.phase)
        val fromActivity = fromImmediate.on(SessionEvent.SomethingDifferent)
        assertNotEquals(fromImmediate.activity, fromActivity.activity)
        val checkpoint = fromActivity.on(SessionEvent.CompleteActivity)
        val fromCheckpoint = checkpoint.on(SessionEvent.SomethingDifferent)
        assertEquals(Phase.FOLLOW_UP, fromCheckpoint.phase)
        assertFalse(fromCheckpoint.activity in checkpoint.completed)
    }

    @Test
    fun `explicit switch goes to the chosen activity`() {
        val s = AnchorCore.start(Experience.PHYSICAL_ALARM, ctx, 1L).on(SessionEvent.SwitchTo(ActivityId.PATTERN_RESTORE))
        assertEquals(ActivityId.PATTERN_RESTORE, s.activity)
        assertEquals(Phase.STABILIZE, s.phase)
    }

    @Test
    fun `pause and resume keep the place`() {
        val s = AnchorCore.start(Experience.JUST_START, ctx, 1L)
        val paused = s.on(SessionEvent.Pause)
        assertTrue(paused.paused)
        assertEquals(s.activity, paused.activity)
        val resumed = paused.on(SessionEvent.Resume)
        assertFalse(resumed.paused)
        assertEquals(s.copy(paused = false), resumed)
    }

    @Test
    fun `exit is possible from every phase`() {
        var s = AnchorCore.start(Experience.THOUGHT_SPIRAL, ctx, 1L)
        val phases = mutableListOf<SessionState>()
        phases += s
        s = s.on(SessionEvent.CompleteImmediate); phases += s
        s = s.on(SessionEvent.CompleteActivity); phases += s
        s = s.on(SessionEvent.ReadyToMoveOn); phases += s
        s = s.on(SessionEvent.FinishRating); phases += s
        phases.forEach { assertTrue("exit from ${it.phase}", it.on(SessionEvent.Exit).isOver) }
    }

    @Test
    fun `ended sessions ignore further events`() {
        val ended = AnchorCore.start(Experience.JUST_START, ctx, 1L).on(SessionEvent.Exit)
        assertEquals(ended, ended.on(SessionEvent.Continue))
    }

    @Test
    fun `moving on mid-activity counts the activity for rating`() {
        val s = AnchorCore.start(Experience.JUST_START, ctx, 1L)
        val moved = s.on(SessionEvent.ReadyToMoveOn)
        assertEquals(Phase.RATING, moved.phase)
        assertEquals(listOf(s.activity), moved.completed)
    }

    @Test
    fun `ratings can be turned off`() {
        val noRate = ctx.copy(askForRatings = false)
        val s = AnchorCore.start(Experience.JUST_START, noRate, 1L).on(SessionEvent.CompleteActivity, noRate).on(SessionEvent.ReadyToMoveOn, noRate)
        assertEquals(Phase.TRANSITION, s.phase)
    }

    @Test
    fun `short sessions go straight to moving on, longer ones add a follow-up`() {
        val short = ctx.copy(sessionLength = SessionLength.SHORT)
        val s1 = AnchorCore.start(Experience.JUST_START, short, 1L).on(SessionEvent.CompleteActivity, short)
        assertEquals(Phase.RATING, s1.phase)

        val longer = ctx.copy(sessionLength = SessionLength.LONGER)
        val s2 = AnchorCore.start(Experience.JUST_START, longer, 1L).on(SessionEvent.CompleteActivity, longer)
        assertEquals(Phase.FOLLOW_UP, s2.phase)
        assertEquals(Phase.CHECKPOINT, s2.on(SessionEvent.CompleteActivity, longer).phase)
    }

    @Test
    fun `steady-self plan runs the user's own sequence`() {
        val plan = listOf(ActivityId.NEXT_OBJECT, ActivityId.PATTERN_RESTORE, ActivityId.PRACTICAL_NEXT_STEP)
        val c = ctx.copy(plan = plan, planEnabled = true)
        var s = AnchorCore.start(Experience.JUST_START, c, 1L)
        assertTrue(s.usingPlan)
        assertEquals(ActivityId.NEXT_OBJECT, s.activity)
        s = s.on(SessionEvent.CompleteActivity, c)
        assertEquals(ActivityId.PATTERN_RESTORE, s.activity)
        s = s.on(SessionEvent.Skip, c)
        assertEquals(ActivityId.PRACTICAL_NEXT_STEP, s.activity)
        s = s.on(SessionEvent.CompleteActivity, c)
        assertEquals(Phase.CHECKPOINT, s.phase)
        assertFalse(s.usingPlan)
    }

    @Test
    fun `disabled plan is ignored`() {
        val c = ctx.copy(plan = listOf(ActivityId.NEXT_OBJECT), planEnabled = false)
        assertFalse(AnchorCore.start(Experience.JUST_START, c, 1L).usingPlan)
    }

    @Test
    fun `single activity sessions return to the checkpoint`() {
        val s = AnchorCore.startSingle(ActivityId.ACCEPTANCE_PRACTICE, ctx, 1L)
        assertEquals(ActivityId.ACCEPTANCE_PRACTICE, s.activity)
        assertEquals(Phase.CHECKPOINT, s.on(SessionEvent.CompleteActivity).phase)
    }

    @Test
    fun `unhelpful ratings change what the engine schedules`() {
        val c = ctx.copy(ratings = mapOf(ActivityId.ORBIT to -9))
        val s = AnchorCore.start(Experience.PHYSICAL_ALARM, c, 1L).on(SessionEvent.CompleteImmediate, c)
        assertNotEquals(ActivityId.ORBIT, s.activity)
    }

    @Test
    fun `immediate actions rotate between sessions`() {
        val a = AnchorCore.start(Experience.PHYSICAL_ALARM, ctx.copy(rotation = 0), 1L).immediate
        val b = AnchorCore.start(Experience.PHYSICAL_ALARM, ctx.copy(rotation = 1), 1L).immediate
        assertNotEquals(a, b)
    }

    @Test
    fun `session codec round-trips through process death`() {
        var s = AnchorCore.start(Experience.DISCONNECTED, ctx, 12345L)
        s = s.on(SessionEvent.CompleteImmediate).on(SessionEvent.Skip).on(SessionEvent.Pause)
        val decoded = SessionCodec.decode(SessionCodec.encode(s))
        assertEquals(s.copy(reasons = decoded!!.reasons), decoded)
        assertEquals(s.reasons.size, decoded.reasons.size)

        val immediate = AnchorCore.start(Experience.OVERWHELMED, ctx, 9L)
        assertEquals(immediate, SessionCodec.decode(SessionCodec.encode(immediate))!!.copy(reasons = immediate.reasons))
    }

    @Test
    fun `codec rejects garbage safely`() {
        assertNull(SessionCodec.decode(null))
        assertNull(SessionCodec.decode(""))
        assertNull(SessionCodec.decode("nonsense|x"))
        assertNull(SessionCodec.decode("s1|NOPE|IMMEDIATE||||||0|0|0|0|OK|0|"))
    }
}
