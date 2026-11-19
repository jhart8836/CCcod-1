package com.anchorone.app.guidance

import com.anchorone.app.engine.Experience

/**
 * STILLPOINT script library. Every line has a full and a short form ("Less talking").
 * Written to be grounded and plain: no promises of safety, no forced breathing,
 * no affirmations, no repeated symptom checks.
 */
object GuidanceLibrary {

    data class Line(val full: String, val short: String)

    enum class Kind { OPEN, GUIDE, TASK, ACK_DONE, LESS, MORE, CLOSE, SWITCH }

    private fun l(full: String, short: String) = Line(full, short)

    private val open = listOf(
        l("I'll give you short, simple things to do. Use the buttons whenever you want something different.", "Short instructions. Buttons any time."),
        l("I'm going to keep this simple. One small thing at a time. Change anything with the buttons below.", "One small thing at a time."),
        l("Let's start with something easy. You can stop or switch whenever you like.", "Starting easy. Stop any time."),
    )

    private val generalGuide = listOf(
        l("Let your eyes land on one thing in front of you. Stay with it for a few seconds.", "Pick one thing to look at."),
        l("You don't have to fix this feeling right now. Just do the next small thing.", "Just the next small thing."),
        l("Notice the weight of the phone in your hand.", "Notice the phone's weight."),
        l("If your mind runs ahead, that's fine. Bring it back to what's in front of you.", "Back to what's in front of you."),
        l("Feel where your body meets the chair, the floor, or whatever is holding you up.", "Feel what's holding you up."),
        l("Look for the oldest object you can see from where you are.", "Find the oldest object."),
        l("Listen for the quietest sound around you.", "Find the quietest sound."),
        l("Move your toes inside your shoes or socks.", "Move your toes."),
        l("Feelings like this tend to rise and shift. You don't have to watch them closely.", "No need to watch the feeling."),
        l("Find one thing you can see that has a straight line in it.", "Find a straight line."),
        l("Let your hands rest on something solid for a moment.", "Hands on something solid."),
        l("It's fine if this feels uncomfortable. You can still do small things while it's here.", "Uncomfortable is allowed. Keep going."),
    )

    private val experienceGuide: Map<Experience, List<Line>> = mapOf(
        Experience.PHYSICAL_ALARM to listOf(
            l("Your body has turned its alarm up. You don't need to argue with it. Keep your hands busy.", "Keep your hands busy."),
            l("Sensations can be loud without needing anything from you right now.", "Loud doesn't mean you must act."),
            l("Press your heels down, then let them rest.", "Heels down, then rest."),
            l("Trace the edge of the phone slowly with one finger.", "Trace the phone's edge."),
            l("Let your shoulders sit a little lower. Nothing more than that.", "Shoulders a little lower."),
        ),
        Experience.THOUGHT_SPIRAL to listOf(
            l("Thoughts are arriving fast. You don't have to answer each one.", "You don't have to answer them."),
            l("Try putting 'I notice I'm thinking that' in front of the loudest thought.", "\"I notice I'm thinking that…\""),
            l("Describe one thing you can see in plain words, like a list. Colour, shape, size.", "Describe one object plainly."),
            l("A thought can stay in the room without you following it anywhere.", "Let the thought stay. Don't follow."),
            l("You don't need to solve anything in the next few minutes.", "Nothing to solve right now."),
        ),
        Experience.DISCONNECTED to listOf(
            l("Touch something with a texture. Rough, smooth, soft. Give it a word.", "Touch a texture. Name it."),
            l("Say today's day of the week, quietly or in your head.", "Say today's day."),
            l("Look at your hands. Move each finger once.", "Move each finger once."),
            l("Find something red, or the closest thing to red.", "Find something red."),
            l("Name three things in this room you could pick up.", "Three things you could pick up."),
        ),
        Experience.OVERWHELMED to listOf(
            l("One thing at a time. Right now, that's just listening to this.", "Just listen to this."),
            l("Everything else can wait for a minute.", "The rest can wait a minute."),
            l("If there's noise, it's fine to turn away from it or cover one ear.", "Turn away from the noise."),
            l("Put one thing down. Anything.", "Put one thing down."),
            l("You don't have to decide anything yet.", "No decisions yet."),
        ),
    )

    private val tasks = listOf(
        l("Find five things with straight edges. Tap Done when you have.", "Five straight edges."),
        l("Touch three different textures near you. Tap Done when you have.", "Three textures."),
        l("Move one object a little closer to you.", "Move one object closer."),
        l("Silently name everything on the nearest surface.", "Name what's on the nearest surface."),
        l("Count the corners you can see in this room.", "Count the corners."),
        l("Pick up something small and turn it over twice.", "Turn a small object over twice."),
        l("Find the lightest-coloured thing you can see.", "Find the lightest colour."),
        l("If you can, stand up, then sit back down.", "Stand, then sit."),
        l("Open and close one hand slowly, five times.", "Open and close a hand, five times."),
        l("Find something that makes a sound when you tap it. Tap it once.", "Tap something that makes a sound."),
        l("Look out of a window, or at the furthest wall, and name what you see.", "Name what's furthest away."),
    )

    private val ackDone = listOf(
        l("Okay.", "Okay."),
        l("Good. Here's the next one.", "Next."),
        l("Alright.", "Alright."),
        l("Done. Let's keep going.", "Keep going."),
    )

    private val less = listOf(l("Okay. I'll keep it short.", "Short from now on."))
    private val more = listOf(l("Okay. A bit more guidance.", "More guidance."))

    private val switch = listOf(
        l("Let's try something different.", "Something different."),
        l("Switching to another activity.", "Switching."),
    )

    private val close = listOf(
        l("Okay. Let's get you back to your day. Pick the very next thing you'll do.", "Back to your day. Pick the next thing."),
        l("Alright. You don't need to feel perfect to carry on. Choose one small next step.", "Choose one next step."),
        l("Let's move on. Choose what comes next, and start with just the first part of it.", "Start the first part of what's next."),
    )

    fun lines(kind: Kind, experience: Experience?): List<Line> = when (kind) {
        Kind.OPEN -> open
        Kind.GUIDE -> (experience?.let { experienceGuide[it] }.orEmpty()) + generalGuide
        Kind.TASK -> tasks
        Kind.ACK_DONE -> ackDone
        Kind.LESS -> less
        Kind.MORE -> more
        Kind.SWITCH -> switch
        Kind.CLOSE -> close
    }

    fun allLines(): List<Line> = Kind.entries.flatMap { k ->
        if (k == Kind.GUIDE) Experience.entries.flatMap { lines(k, it) }.distinct() else lines(k, null)
    }
}
