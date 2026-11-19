package com.anchorone.app.engine

/**
 * Immutable session snapshot. ANCHOR CORE is a pure reducer over this state:
 * (state, event, context) -> state. No timers, no sensors, no hidden inputs.
 */
data class SessionState(
    val experience: Experience,
    val phase: Phase,
    val activity: ActivityId? = null,
    val reasons: List<String> = emptyList(),
    val immediate: ImmediateAction? = null,
    val completed: List<ActivityId> = emptyList(),
    val skipped: Set<ActivityId> = emptySet(),
    val paused: Boolean = false,
    /** Started from the toolkit for one specific activity. */
    val single: Boolean = false,
    /** Running the user's Steady-Self Plan. */
    val usingPlan: Boolean = false,
    val planIndex: Int = 0,
    val energy: Energy = Energy.OK,
    val startedAt: Long = 0L,
) {
    val isActivityPhase: Boolean get() = phase == Phase.STABILIZE || phase == Phase.FOLLOW_UP
    val isOver: Boolean get() = phase == Phase.ENDED
}

sealed interface SessionEvent {
    data object CompleteImmediate : SessionEvent
    data object CompleteActivity : SessionEvent
    data object Skip : SessionEvent
    data class SwitchTo(val activity: ActivityId) : SessionEvent
    data object SomethingDifferent : SessionEvent
    data object Continue : SessionEvent
    data object ReadyToMoveOn : SessionEvent
    data object Pause : SessionEvent
    data object Resume : SessionEvent
    data object FinishRating : SessionEvent
    data object Finish : SessionEvent
    data object Exit : SessionEvent
}

object AnchorCore {

    fun start(experience: Experience, context: EngineContext, now: Long): SessionState {
        val base = SessionState(experience = experience, phase = Phase.STABILIZE, energy = context.energy, startedAt = now)
        if (experience == Experience.JUST_START) {
            val plan = usablePlan(context)
            if (plan.isNotEmpty()) {
                return base.copy(
                    activity = plan.first(),
                    reasons = listOf("Step 1 of the plan you made"),
                    usingPlan = true,
                    planIndex = 0,
                )
            }
            val pick = ActivitySelector.leastDemanding(context)
            return base.copy(activity = pick.activity, reasons = listOf("Asks almost nothing of you") + pick.reasons.take(1))
        }
        return base.copy(
            phase = Phase.IMMEDIATE,
            immediate = ImmediateActions.pick(experience, context.rotation),
        )
    }

    fun startSingle(activity: ActivityId, context: EngineContext, now: Long): SessionState =
        SessionState(
            experience = Experience.JUST_START,
            phase = Phase.STABILIZE,
            activity = activity,
            reasons = listOf("You chose this"),
            single = true,
            energy = context.energy,
            startedAt = now,
        )

    fun reduce(state: SessionState, event: SessionEvent, context: EngineContext): SessionState {
        if (state.isOver) return state
        return when (event) {
            SessionEvent.Pause -> state.copy(paused = true)
            SessionEvent.Resume -> state.copy(paused = false)
            SessionEvent.Exit -> state.copy(phase = Phase.ENDED, paused = false)
            SessionEvent.Finish -> state.copy(phase = Phase.ENDED, paused = false)
            else -> reduceActive(state.copy(paused = false), event, context)
        }
    }

    private fun reduceActive(state: SessionState, event: SessionEvent, context: EngineContext): SessionState =
        when (event) {
            SessionEvent.CompleteImmediate -> if (state.phase == Phase.IMMEDIATE) enterStabilize(state, context) else state

            SessionEvent.CompleteActivity -> if (state.isActivityPhase) afterActivity(state, context) else state

            SessionEvent.Skip -> when (state.phase) {
                Phase.IMMEDIATE -> enterStabilize(state, context)
                Phase.STABILIZE, Phase.FOLLOW_UP -> skipActivity(state, context)
                Phase.RATING -> state.copy(phase = Phase.TRANSITION)
                Phase.CHECKPOINT -> moveOn(state, context)
                Phase.TRANSITION -> state.copy(phase = Phase.ENDED)
                Phase.ENDED -> state
            }

            is SessionEvent.SwitchTo -> {
                val phase = if (state.phase == Phase.STABILIZE || state.phase == Phase.IMMEDIATE) Phase.STABILIZE else Phase.FOLLOW_UP
                state.copy(
                    phase = phase,
                    activity = event.activity,
                    reasons = listOf("You chose this"),
                    immediate = null,
                    usingPlan = false,
                )
            }

            SessionEvent.SomethingDifferent -> somethingDifferent(state, context)

            SessionEvent.Continue -> if (state.phase == Phase.CHECKPOINT) enterFollowUp(state, context) else state

            SessionEvent.ReadyToMoveOn -> moveOn(state, context)

            SessionEvent.FinishRating -> if (state.phase == Phase.RATING) state.copy(phase = Phase.TRANSITION) else state

            else -> state
        }

    private fun enterStabilize(state: SessionState, context: EngineContext): SessionState {
        val pathway = ActivityCatalog.pathway(state.experience)
        val pick = ActivitySelector.best(pathway.stabilize, context, exclude = state.skipped)
        return state.copy(phase = Phase.STABILIZE, activity = pick.activity, reasons = pick.reasons, immediate = null)
    }

    private fun enterFollowUp(state: SessionState, context: EngineContext): SessionState {
        val pathway = ActivityCatalog.pathway(state.experience)
        val exclude = state.skipped + state.completed + listOfNotNull(state.activity)
        val ranked = ActivitySelector.rank(pathway.followUp, context, exclude)
        val pick = ranked.firstOrNull { it.activity !in exclude }
            ?: ActivitySelector.best(ActivityCatalog.switchCandidates(state.experience), context, exclude)
        return state.copy(phase = Phase.FOLLOW_UP, activity = pick.activity, reasons = pick.reasons)
    }

    private fun afterActivity(state: SessionState, context: EngineContext): SessionState {
        val done = state.copy(completed = state.completed + listOfNotNull(state.activity))
        if (done.single) return done.copy(phase = Phase.CHECKPOINT)

        if (done.usingPlan) {
            val plan = usablePlan(context)
            val next = done.planIndex + 1
            return if (next < plan.size) {
                done.copy(
                    phase = Phase.FOLLOW_UP,
                    activity = plan[next],
                    planIndex = next,
                    reasons = listOf("Step ${next + 1} of the plan you made"),
                )
            } else {
                done.copy(phase = Phase.CHECKPOINT, usingPlan = false)
            }
        }

        return when {
            context.sessionLength == SessionLength.SHORT -> moveOn(done, context)
            context.sessionLength == SessionLength.LONGER && state.phase == Phase.STABILIZE -> enterFollowUp(done, context)
            else -> done.copy(phase = Phase.CHECKPOINT)
        }
    }

    private fun skipActivity(state: SessionState, context: EngineContext): SessionState {
        val current = state.activity
        val skipped = state.skipped + listOfNotNull(current)
        val base = state.copy(skipped = skipped)

        if (state.usingPlan) {
            val plan = usablePlan(context)
            val next = state.planIndex + 1
            return if (next < plan.size) {
                base.copy(activity = plan[next], planIndex = next, reasons = listOf("Step ${next + 1} of the plan you made"))
            } else {
                base.copy(phase = Phase.CHECKPOINT, usingPlan = false)
            }
        }

        val pathway = ActivityCatalog.pathway(state.experience)
        val list = if (state.phase == Phase.STABILIZE) pathway.stabilize else pathway.followUp
        val exclude = skipped + state.completed
        val remaining = list.filterNot { it in exclude }
        if (remaining.isEmpty()) return base.copy(phase = Phase.CHECKPOINT)
        val pick = ActivitySelector.best(remaining, context, exclude)
        return base.copy(activity = pick.activity, reasons = pick.reasons)
    }

    private fun somethingDifferent(state: SessionState, context: EngineContext): SessionState {
        val exclude = state.skipped + listOfNotNull(state.activity) + state.completed
        var ranked = ActivitySelector.rank(ActivityCatalog.switchCandidates(state.experience), context, exclude)
        ranked = ranked.filterNot { it.activity in exclude }
        val pick = ranked.firstOrNull()
            ?: ActivitySelector.rank(ActivityId.entries, context, setOfNotNull(state.activity)).first()
        val phase = when (state.phase) {
            Phase.IMMEDIATE, Phase.STABILIZE -> Phase.STABILIZE
            else -> Phase.FOLLOW_UP
        }
        return state.copy(
            phase = phase,
            activity = pick.activity,
            reasons = listOf("Something different") + pick.reasons.take(1),
            immediate = null,
            usingPlan = false,
        )
    }

    private fun moveOn(state: SessionState, context: EngineContext): SessionState {
        val completed = if (state.isActivityPhase && state.activity != null && state.activity !in state.completed) {
            // Moving on mid-activity still counts what was tried, so it can be rated.
            state.completed + state.activity
        } else state.completed
        val next = state.copy(completed = completed, usingPlan = false)
        return if (context.askForRatings && completed.isNotEmpty()) next.copy(phase = Phase.RATING)
        else next.copy(phase = Phase.TRANSITION)
    }

    private fun usablePlan(context: EngineContext): List<ActivityId> =
        if (context.planEnabled) context.plan.distinct().take(MAX_PLAN_STEPS) else emptyList()

    const val MAX_PLAN_STEPS = 4
}
