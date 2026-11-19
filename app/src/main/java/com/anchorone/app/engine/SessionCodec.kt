package com.anchorone.app.engine

/**
 * Compact, dependency-free text encoding of a session so it survives process death
 * (stored in SavedStateHandle) and can be resumed after an accidental exit.
 */
object SessionCodec {

    private const val SEP = "|"
    private const val LIST = ","
    private const val VERSION = "s1"

    fun encode(s: SessionState): String = listOf(
        VERSION,
        s.experience.name,
        s.phase.name,
        s.activity?.name.orEmpty(),
        s.immediate?.id.orEmpty(),
        s.completed.joinToString(LIST) { it.name },
        s.skipped.joinToString(LIST) { it.name },
        if (s.paused) "1" else "0",
        if (s.single) "1" else "0",
        if (s.usingPlan) "1" else "0",
        s.planIndex.toString(),
        s.energy.name,
        s.startedAt.toString(),
        s.reasons.joinToString(LIST) { it.replace(LIST, ";").replace(SEP, "/") },
    ).joinToString(SEP)

    fun decode(text: String?): SessionState? {
        if (text.isNullOrBlank()) return null
        return runCatching {
            val p = text.split(SEP)
            require(p.size >= 14 && p[0] == VERSION)
            val experience = Experience.valueOf(p[1])
            SessionState(
                experience = experience,
                phase = Phase.valueOf(p[2]),
                activity = p[3].takeIf { it.isNotEmpty() }?.let { ActivityId.valueOf(it) },
                immediate = p[4].takeIf { it.isNotEmpty() }?.let { id ->
                    ImmediateActions.forExperience(experience).firstOrNull { it.id == id }
                },
                completed = activities(p[5]),
                skipped = activities(p[6]).toSet(),
                paused = p[7] == "1",
                single = p[8] == "1",
                usingPlan = p[9] == "1",
                planIndex = p[10].toInt(),
                energy = Energy.valueOf(p[11]),
                startedAt = p[12].toLong(),
                reasons = p[13].split(LIST).filter { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun activities(s: String): List<ActivityId> =
        s.split(LIST).filter { it.isNotBlank() }.mapNotNull { n -> ActivityId.entries.firstOrNull { it.name == n } }
}
