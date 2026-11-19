package com.anchorone.app.engine

/**
 * The experience a person reports. Never required: JUST_START exists so nobody
 * has to label what is happening before getting help.
 */
enum class Experience(val label: String, val detail: String) {
    PHYSICAL_ALARM("Physical alarm", "My body feels alarmed"),
    THOUGHT_SPIRAL("Thought spiral", "My thoughts are racing"),
    DISCONNECTED("Disconnected", "Things feel unreal or far away"),
    OVERWHELMED("Overwhelmed", "Too much at once"),
    JUST_START("Just help me start", "Skip choosing. Begin with something simple.");
}

/** Broad families users can prefer or switch off in Settings. */
enum class Category(val label: String, val detail: String) {
    INTERACTIVE("Interactive", "Touch-based activities on screen"),
    SENSORY("Surroundings", "Noticing real objects, sounds and textures"),
    THINKING("Thought tools", "Unhooking from thoughts, choosing next steps"),
    ACCEPTANCE("Making room", "Letting feelings be present without a fight"),
}

/** Every activity ANCHOR CORE can schedule. New activities only need an entry here and a renderer. */
enum class ActivityId(
    val title: String,
    val tagline: String,
    val category: Category,
    /** 1 = needs almost no thought, 3 = asks for more deliberate attention. */
    val demand: Int,
    /** True when the activity relies on continuous animation (reduced-motion variants exist). */
    val motionHeavy: Boolean,
    val rationale: String,
) {
    ORBIT(
        "Orbit", "Guide a soft light with your finger", Category.INTERACTIVE, 1, true,
        "Steering something with your hand gives attention a simple, external place to rest.",
    ),
    PATTERN_RESTORE(
        "Pattern Restore", "Put a simple pattern back together", Category.INTERACTIVE, 2, false,
        "A small, finishable task with your hands and eyes. Engagement, not performance.",
    ),
    NEXT_OBJECT(
        "The Next Object", "Find real things around you, one at a time", Category.SENSORY, 1, false,
        "Pointing attention at ordinary things in the room reconnects you with where you are.",
    ),
    FLOW_FIELD(
        "Flow Field", "Shape drifting light with your touch", Category.INTERACTIVE, 1, true,
        "A low-effort, open-ended thing to do with your hands. Nothing to get right.",
    ),
    SINGLE_THREAD(
        "Single Thread", "One tiny task at a time", Category.SENSORY, 1, false,
        "When everything is too much, a single concrete step is easier than a plan.",
    ),
    THOUGHT_UNHOOKING(
        "Thought Unhooking", "See a thought as a thought", Category.THINKING, 2, false,
        "From ACT: noticing \"I'm having the thought that…\" loosens its grip without arguing with it.",
    ),
    ATTENTION_RELOCATION(
        "Attention Relocation", "Practise moving your attention on purpose", Category.SENSORY, 2, false,
        "Choosing where attention goes, and returning when it wanders, is a trainable skill.",
    ),
    PRACTICAL_NEXT_STEP(
        "Practical Next Step", "Pick one manageable action", Category.THINKING, 2, false,
        "From CBT behavioural activation: small, concrete actions rebuild momentum and agency.",
    ),
    ACCEPTANCE_PRACTICE(
        "Making Room", "Let a feeling be there without a fight", Category.ACCEPTANCE, 2, false,
        "From ACT: struggling with a sensation often amplifies it. Making room can soften the struggle.",
    ),
}

/** Where a session currently is. Every phase can be left at any time. */
enum class Phase {
    /** One very small action that needs almost no thought. */
    IMMEDIATE,

    /** The main interactive stabilisation activity. */
    STABILIZE,

    /** "What next?" — continue, change, or move on. Never a symptom check. */
    CHECKPOINT,

    /** Optional second activity. */
    FOLLOW_UP,

    /** Optional activity ratings. */
    RATING,

    /** Re-entry Ramp: practical transition back into the day. */
    TRANSITION,

    ENDED,
}

/** The person's own statement of how much they can engage with. Never inferred. */
enum class Energy { LOW, OK }

enum class SessionLength(val label: String, val detail: String) {
    SHORT("Short", "One activity, then straight to moving on"),
    STANDARD("Standard", "One activity, then you choose what's next"),
    LONGER("Longer", "Automatically offers a follow-up activity"),
}

/** Interface density — the Cognitive Load Engine. */
enum class Density(val label: String, val detail: String) {
    NORMAL("Normal", "Full interface and choices"),
    SIMPLIFIED("Simplified", "Larger controls, fewer options"),
    MINIMAL("Minimal", "One instruction, one action"),
}

enum class Rating(val label: String, val weight: Int) {
    USEFUL("Useful", 2),
    NEUTRAL("Neutral", 0),
    UNHELPFUL("Not for me", -3),
}
