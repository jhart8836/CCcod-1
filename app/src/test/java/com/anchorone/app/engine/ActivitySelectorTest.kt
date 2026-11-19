package com.anchorone.app.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivitySelectorTest {

    private val ctx = EngineContext()

    @Test
    fun `pathway order is the default ranking`() {
        val list = ActivityCatalog.pathway(Experience.THOUGHT_SPIRAL).stabilize
        val ranked = ActivitySelector.rank(list, ctx).map { it.activity }
        assertEquals(list, ranked)
    }

    @Test
    fun `ranking is deterministic`() {
        val list = ActivityCatalog.pathway(Experience.PHYSICAL_ALARM).stabilize
        val a = ActivitySelector.rank(list, ctx.copy(ratings = mapOf(ActivityId.NEXT_OBJECT to 2)))
        val b = ActivitySelector.rank(list, ctx.copy(ratings = mapOf(ActivityId.NEXT_OBJECT to 2)))
        assertEquals(a, b)
    }

    @Test
    fun `useful rating raises an activity`() {
        val list = ActivityCatalog.pathway(Experience.PHYSICAL_ALARM).stabilize
        val before = ActivitySelector.rank(list, ctx).indexOfFirst { it.activity == ActivityId.NEXT_OBJECT }
        val after = ActivitySelector.rank(list, ctx.copy(ratings = mapOf(ActivityId.NEXT_OBJECT to 4)))
            .indexOfFirst { it.activity == ActivityId.NEXT_OBJECT }
        assertTrue(after < before)
    }

    @Test
    fun `unhelpful rating lowers but never removes an activity`() {
        val list = ActivityCatalog.pathway(Experience.PHYSICAL_ALARM).stabilize
        val ranked = ActivitySelector.rank(list, ctx.copy(ratings = mapOf(ActivityId.ORBIT to -9)))
        assertNotEquals(ActivityId.ORBIT, ranked.first().activity)
        assertTrue(ranked.any { it.activity == ActivityId.ORBIT })
        assertTrue(ranked.first { it.activity == ActivityId.ORBIT }.reasons.any { "less useful" in it })
    }

    @Test
    fun `favourites and preferred categories add weight with explanations`() {
        val list = ActivityCatalog.pathway(Experience.DISCONNECTED).stabilize
        val pick = ActivitySelector.best(list, ctx.copy(favorites = setOf(ActivityId.ATTENTION_RELOCATION), preferredCategories = setOf(Category.SENSORY)))
        assertTrue(pick.reasons.isNotEmpty())
        val fav = ActivitySelector.rank(list, ctx.copy(favorites = setOf(ActivityId.ATTENTION_RELOCATION)))
            .first { it.activity == ActivityId.ATTENTION_RELOCATION }
        assertTrue(fav.reasons.contains("One of your favourites"))
    }

    @Test
    fun `low energy prefers demand 1 activities`() {
        val list = ActivityCatalog.pathway(Experience.THOUGHT_SPIRAL).stabilize // starts with demand-2 unhooking
        val pick = ActivitySelector.best(list, ctx.copy(energy = Energy.LOW))
        assertEquals(1, pick.activity.demand)
    }

    @Test
    fun `disabled categories are dropped unless nothing would remain`() {
        val list = ActivityCatalog.pathway(Experience.OVERWHELMED).stabilize
        val ranked = ActivitySelector.rank(list, ctx.copy(disabledCategories = setOf(Category.SENSORY)))
        assertFalse(ranked.any { it.activity.category == Category.SENSORY })

        val everythingOff = ActivitySelector.rank(list, ctx.copy(disabledCategories = Category.entries.toSet()))
        assertTrue("engine must never return nothing", everythingOff.isNotEmpty())
    }

    @Test
    fun `excluded activities are not offered`() {
        val list = ActivityCatalog.pathway(Experience.PHYSICAL_ALARM).stabilize
        val ranked = ActivitySelector.rank(list, ctx, exclude = setOf(ActivityId.ORBIT))
        assertFalse(ranked.any { it.activity == ActivityId.ORBIT })
    }

    @Test
    fun `excluding everything still yields a choice`() {
        val list = listOf(ActivityId.ORBIT)
        val ranked = ActivitySelector.rank(list, ctx, exclude = setOf(ActivityId.ORBIT))
        assertTrue(ranked.isNotEmpty())
    }

    @Test
    fun `least demanding is always demand 1`() {
        assertEquals(1, ActivitySelector.leastDemanding(ctx).activity.demand)
        assertEquals(1, ActivitySelector.leastDemanding(ctx.copy(ratings = mapOf(ActivityId.FLOW_FIELD to -9))).activity.demand)
    }

    @Test
    fun `every pathway offers distinct stabilise and follow-up content`() {
        Experience.entries.forEach { e ->
            val p = ActivityCatalog.pathway(e)
            assertTrue(p.stabilize.isNotEmpty())
            assertTrue(p.followUp.isNotEmpty())
            assertTrue(ImmediateActions.forExperience(e).isNotEmpty())
        }
        val firsts = Experience.entries.filter { it != Experience.JUST_START }.map { ActivityCatalog.pathway(it).stabilize.first() }
        assertEquals("the four pathways lead with different activities", firsts.size, firsts.toSet().size)
    }

    @Test
    fun `density advisor only suggests from explicit statements and never auto-applies`() {
        assertEquals(null, DensityAdvisor.recommend(Experience.PHYSICAL_ALARM, Energy.OK, Density.NORMAL))
        assertEquals(Density.SIMPLIFIED, DensityAdvisor.recommend(Experience.OVERWHELMED, Energy.OK, Density.NORMAL)?.density)
        assertEquals(Density.MINIMAL, DensityAdvisor.recommend(Experience.OVERWHELMED, Energy.LOW, Density.NORMAL)?.density)
        assertEquals(Density.MINIMAL, DensityAdvisor.recommend(null, Energy.LOW, Density.SIMPLIFIED)?.density)
        assertEquals(null, DensityAdvisor.recommend(Experience.OVERWHELMED, Energy.LOW, Density.MINIMAL))
    }
}
