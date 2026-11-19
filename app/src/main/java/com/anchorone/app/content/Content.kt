package com.anchorone.app.content

/** Small deterministic shuffle so content rotates between sessions without randomness in tests. */
fun <T> List<T>.rotatedShuffle(seed: Int): List<T> {
    if (size < 2) return this
    val rnd = java.util.Random(seed.toLong() * 7919L + 17L)
    return shuffled(rnd)
}

object NextObjectPrompts {
    data class Prompt(val text: String, val hint: String, val level: Int)

    val all: List<Prompt> = listOf(
        // Level 1 — looking
        Prompt("Find something with a straight edge.", "A table, a door frame, a book.", 1),
        Prompt("Find something round.", "A cup, a light, a button.", 1),
        Prompt("Find the lightest-coloured thing you can see.", "", 1),
        Prompt("Find something blue, or the closest thing to blue.", "", 1),
        Prompt("Find something with writing on it.", "A label, a sign, a screen.", 1),
        Prompt("Find a shadow.", "Where is the light coming from?", 1),
        Prompt("Find something that reflects light.", "", 1),
        Prompt("Find the furthest thing you can see.", "", 1),
        Prompt("Find something smaller than your thumb.", "", 1),
        Prompt("Find something that has been there a long time.", "", 1),
        // Level 2 — touch and sound
        Prompt("Touch something with a different texture.", "Rough, smooth, soft, ridged.", 2),
        Prompt("Touch something cool.", "", 2),
        Prompt("Touch something soft.", "Fabric, a cushion, your sleeve.", 2),
        Prompt("Notice one sound beyond this room.", "Traffic, wind, a voice, a hum.", 2),
        Prompt("Notice the quietest sound you can hear.", "", 2),
        Prompt("Notice one sound close to you.", "", 2),
        Prompt("Feel the air on the backs of your hands.", "Warm, cool, still, moving.", 2),
        Prompt("Touch something with a grain or pattern.", "Wood, fabric, stone.", 2),
        // Level 3 — engaging
        Prompt("Pick up a small object. Notice how heavy it is.", "", 3),
        Prompt("Move one object a few centimetres.", "Put it somewhere slightly better.", 3),
        Prompt("Trace the outline of something with a finger.", "", 3),
        Prompt("Find two things that are the same colour.", "", 3),
        Prompt("Choose one object and name three details about it.", "", 3),
        Prompt("Find something you could use right now, and name what for.", "", 3),
    )

    /** One round: 3 looking, 2 touch/sound, 2 engaging — gradual progression. */
    fun round(seed: Int): List<Prompt> =
        all.filter { it.level == 1 }.rotatedShuffle(seed).take(3) +
            all.filter { it.level == 2 }.rotatedShuffle(seed + 1).take(2) +
            all.filter { it.level == 3 }.rotatedShuffle(seed + 2).take(2)

    fun alternative(current: Prompt, exclude: Collection<Prompt>, seed: Int): Prompt =
        all.filter { it.level == current.level && it != current && it !in exclude }.rotatedShuffle(seed).firstOrNull()
            ?: all.first { it != current }
}

object SingleThreadTasks {
    enum class Kind { TAP, HOLD, SLIDE, CHOICE }

    data class Task(
        val text: String,
        val kind: Kind = Kind.TAP,
        val alternative: String? = null,
        val options: List<String> = emptyList(),
    )

    private val nextChoices = listOf("Stay here a minute", "Stand up", "Go back to what I was doing", "Do one more thread")

    val threads: List<List<Task>> = listOf(
        listOf(
            Task("Look at the top edge of your screen.", alternative = "Look at the bottom edge instead."),
            Task("Press and hold the circle until it fills.", Kind.HOLD),
            Task("Look away from the screen. Find the nearest door or window.", alternative = "Find the nearest wall."),
            Task("Move your feet, even slightly.", alternative = "Move one hand instead."),
            Task("Slide to set everything else aside for now.", Kind.SLIDE),
            Task("If there's water nearby, take one sip.", alternative = "Swallow once instead, or just tap."),
            Task("Pick what comes next.", Kind.CHOICE, options = nextChoices),
        ),
        listOf(
            Task("Put the phone in your other hand.", alternative = "Rest the phone on your leg or a table."),
            Task("Look at something that isn't moving."),
            Task("Hold the circle. Let it fill in its own time.", Kind.HOLD),
            Task("Name the room you're in.", alternative = "Name the building or street instead."),
            Task("Straighten one thing near you.", alternative = "Just touch one thing near you."),
            Task("Silence or close one thing you don't need right now. Then slide.", Kind.SLIDE, alternative = "Slide to continue."),
            Task("Pick what comes next.", Kind.CHOICE, options = nextChoices),
        ),
        listOf(
            Task("Notice which way you're facing."),
            Task("Find something green, or the closest thing to green."),
            Task("Hold the circle until it fills.", Kind.HOLD),
            Task("Open your hands. Let your fingers spread.", alternative = "Rest your hands on your legs."),
            Task("Find your keys, wallet or bag. Just know where they are.", alternative = "Find where the exit is."),
            Task("Slide to continue.", Kind.SLIDE),
            Task("Pick what comes next.", Kind.CHOICE, options = nextChoices),
        ),
    )

    fun thread(seed: Int): List<Task> = threads[Math.floorMod(seed, threads.size)]
}

object ReEntryContent {
    data class Anchor(val label: String, val steps: List<String>)

    val defaults: List<Anchor> = listOf(
        Anchor(
            "Back to work or study",
            listOf("Put the phone face-down when you're ready.", "Open the one thing you were working on.", "Do only the first two minutes of it."),
        ),
        Anchor(
            "Back to a conversation",
            listOf("Take a moment before you rejoin.", "If it helps, say: \"Sorry, I needed a minute.\"", "Listen to the next thing someone says."),
        ),
        Anchor(
            "Head somewhere",
            listOf("Check you have your keys, phone and bag.", "Pick the first point on the route.", "Walk to it at an ordinary pace."),
        ),
        Anchor(
            "Look after myself",
            listOf("Get some water or something small to eat.", "Change one thing: the light, the temperature, the noise.", "Then pick the next small thing."),
        ),
        Anchor(
            "Rest for a bit",
            listOf("Find somewhere comfortable.", "Choose something easy: music, a show, a window.", "Let resting be the task for now."),
        ),
    )

    fun custom(label: String): Anchor =
        Anchor(label, listOf("Put the phone down when you're ready.", "Start: ${label.trim()}.", "Just the first few minutes. That's enough."))
}

object NextStepContent {
    data class Step(val text: String, val smaller: String)
    data class Group(val label: String, val steps: List<Step>)

    val groups: List<Group> = listOf(
        Group(
            "Back to what I was doing",
            listOf(
                Step("Return to the task for 10 minutes", "Return to the task for 2 minutes"),
                Step("Reply to one message", "Read one message — no reply needed"),
                Step("Write down the very next step", "Write one word about the next step"),
            ),
        ),
        Group(
            "Look after my body",
            listOf(
                Step("Drink a full glass of water", "Take one sip of water"),
                Step("Eat something small", "Get a snack within reach"),
                Step("Wash your face or hands", "Run cool water over your wrists"),
            ),
        ),
        Group(
            "Change my surroundings",
            listOf(
                Step("Step outside for five minutes", "Open a window or door"),
                Step("Move to a quieter space", "Turn away from the noise"),
                Step("Tidy one surface", "Put one object where it belongs"),
            ),
        ),
        Group(
            "Reach out",
            listOf(
                Step("Call someone you trust", "Send someone a one-line message"),
                Step("Tell someone nearby you need a minute", "Let them know you're stepping away"),
            ),
        ),
        Group(
            "Rest",
            listOf(
                Step("Sit somewhere comfortable for ten minutes", "Sit down for two minutes"),
                Step("Put on something familiar to listen to", "Choose one song"),
            ),
        ),
    )
}

object ThoughtContent {
    val commonThoughts = listOf(
        "Something bad is going to happen",
        "I can't cope with this",
        "What if I lose control?",
        "Something is wrong with me",
        "People will notice",
        "This will never stop",
        "I have to fix this right now",
    )

    private fun clause(thought: String): String {
        val t = thought.trim().trimEnd('.', '!', '?').ifEmpty { "something difficult" }
        // Keep "I" capitalised ("I can't cope"), lowercase everything else ("Something bad…").
        return if (t == "I" || t.startsWith("I ") || t.startsWith("I'")) t else t.replaceFirstChar { it.lowercase() }
    }

    fun unhooked(thought: String): String = "I'm having the thought that ${clause(thought)}."

    fun noticed(thought: String): String = "I notice I'm having the thought that ${clause(thought)}."
}

object AttentionContent {
    val anchors = listOf(
        "A sound you can hear",
        "Your hands",
        "Something blue",
        "The floor under you",
        "A source of light",
        "Something far away",
        "The weight of the phone",
        "Your feet",
    )
}
