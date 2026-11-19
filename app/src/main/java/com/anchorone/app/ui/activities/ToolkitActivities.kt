package com.anchorone.app.ui.activities

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.anchorone.app.content.AttentionContent
import com.anchorone.app.content.NextStepContent
import com.anchorone.app.content.ThoughtContent
import com.anchorone.app.content.rotatedShuffle
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.ui.components.Beads
import com.anchorone.app.ui.components.BodyText
import com.anchorone.app.ui.components.Chip
import com.anchorone.app.ui.components.ChoiceCard
import com.anchorone.app.ui.components.GhostButton
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.components.SecondaryButton
import com.anchorone.app.ui.screens.fieldColors
import com.anchorone.app.ui.theme.Anchor
import com.anchorone.app.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
private fun StepColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

@Composable
private fun StepTitle(text: String) {
    val env = Anchor.env
    Text(
        text,
        style = if (env.simplified) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.headlineMedium,
        color = Anchor.colors.text,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
}

// ---------------------------------------------------------------------------------------------
// THOUGHT UNHOOKING (ACT defusion). Recognise a thought as a mental event; no disputing.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThoughtUnhookingActivity(onDone: () -> Unit, onSwitchTo: (ActivityId) -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    var step by rememberSaveable { mutableIntStateOf(0) }
    var thought by rememberSaveable { mutableStateOf("") }

    when (step) {
        0 -> StepColumn {
            StepTitle("What's the loudest thought right now?")
            if (!env.minimal) BodyText("Pick the closest, type it, or skip. You won't be asked to argue with it.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ThoughtContent.commonThoughts.take(if (env.minimal) 4 else 7).forEach { t ->
                    Chip(t, thought == t, { thought = t; step = 1 })
                }
            }
            if (!env.minimal) {
                OutlinedTextField(
                    value = thought,
                    onValueChange = { thought = it.take(120) },
                    label = { Text("Or in your own words") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    colors = fieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            PrimaryButton(if (thought.isBlank()) "Skip naming it" else "Continue", { step = 1 })
        }
        1 -> StepColumn {
            var layer by rememberSaveable { mutableIntStateOf(0) }
            Spacer(Modifier.height(12.dp))
            Text(
                "“${thought.ifBlank { "Something difficult" }.trimEnd('.')}”",
                style = MaterialTheme.typography.titleLarge, color = c.textDim,
            )
            if (layer >= 1) {
                Text(ThoughtContent.unhooked(thought), style = MaterialTheme.typography.headlineSmall, color = c.text)
            }
            if (layer >= 2) {
                Text(ThoughtContent.noticed(thought), style = MaterialTheme.typography.headlineSmall, color = c.accent)
                if (!env.minimal) BodyText("Same words, a little more distance. It's a thought you're having — you don't have to prove or disprove it.")
            }
            Spacer(Modifier.height(8.dp))
            when (layer) {
                0 -> PrimaryButton("Add “I'm having the thought that…”", { layer = 1 })
                1 -> PrimaryButton("Add “I notice…”", { layer = 2 })
                else -> PrimaryButton("Continue", { step = 2 })
            }
        }
        2 -> ThoughtSetAside(thought, onNext = { step = 3 })
        else -> StepColumn {
            StepTitle("If this thought were just background noise, what would you do next?")
            ChoiceCard("Pick a practical next step", null, { onSwitchTo(ActivityId.PRACTICAL_NEXT_STEP) })
            ChoiceCard("Something for my hands", null, { onSwitchTo(ActivityId.PATTERN_RESTORE) })
            ChoiceCard("Notice my surroundings", null, { onSwitchTo(ActivityId.NEXT_OBJECT) })
            PrimaryButton("I'm done with this", onDone)
        }
    }
}

/** The thought sits on a card in the centre. Swipe it aside — it stays visible at the edge, allowed to be there. */
@Composable
private fun ThoughtSetAside(thought: String, onNext: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    val scope = rememberCoroutineScope()
    val x = remember { Animatable(0f) }
    var aside by rememberSaveable { mutableStateOf(false) }
    var times by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        StepTitle(if (!aside) "Swipe the thought to the side." else "It can stay there, at the edge.")
        if (!env.minimal) {
            BodyText(if (!aside) "Not away — just out of the centre." else "If it comes back to the middle, that's normal. Move it again.")
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val limit = constraints.maxWidth * 0.36f
            LaunchedEffect(aside) {
                val target = if (aside) limit else 0f
                if (env.reducedMotion) x.snapTo(target) else x.animateTo(target, spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow))
            }
            Box(
                Modifier
                    .offset { IntOffset(x.value.roundToInt(), 0) }
                    .graphicsLayer {
                        val p = (abs(x.value) / limit).coerceIn(0f, 1f)
                        scaleX = 1f - 0.45f * p
                        scaleY = 1f - 0.45f * p
                        alpha = 1f - 0.45f * p
                    }
                    .widthIn(max = 300.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(c.surfaceHigh, c.surface)))
                    .pointerInput(aside) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                if (abs(x.value) > limit * 0.4f) {
                                    aside = true
                                    times++
                                    haptics.confirm()
                                    scope.launch { x.animateTo(if (x.value > 0) limit else -limit, tween(if (env.reducedMotion) 0 else 450)) }
                                } else {
                                    scope.launch { x.animateTo(if (aside) limit else 0f, tween(300)) }
                                }
                            },
                        ) { change, amount ->
                            change.consume()
                            scope.launch { x.snapTo((x.value + amount).coerceIn(-limit * 1.2f, limit * 1.2f)) }
                        }
                    }
                    .padding(24.dp)
                    .semantics { contentDescription = "Thought card: $thought. Swipe sideways to set it aside." },
            ) {
                Text(thought.ifBlank { "Something difficult" }, style = MaterialTheme.typography.titleLarge, color = c.text)
            }
        }
        if (aside) {
            SecondaryButton("It came back", { aside = false })
            PrimaryButton("Continue", onNext)
        } else {
            GhostButton("Set it aside for me", { aside = true; times++ }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// ATTENTION RELOCATION. Voluntary shifting between chosen external anchors.
// ---------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AttentionRelocationActivity(seed: Int, onDone: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    val defaults = remember { AttentionContent.anchors.rotatedShuffle(seed).take(3) }
    val chosen = remember { mutableStateListOf<String>().apply { addAll(defaults) } }
    var practising by rememberSaveable { mutableStateOf(false) }
    var shifts by rememberSaveable { mutableIntStateOf(0) }
    val pulse = remember { Animatable(1f) }

    LaunchedEffect(shifts) {
        if (shifts > 0 && !env.reducedMotion) {
            pulse.snapTo(1.08f)
            pulse.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
        }
    }

    if (!practising) {
        StepColumn {
            StepTitle("Choose three places to put your attention.")
            if (!env.minimal) BodyText("Three are already picked. Change them or just start.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AttentionContent.anchors.forEach { a ->
                    val on = a in chosen
                    Chip(a, on, {
                        if (on) chosen.remove(a) else if (chosen.size < 3) chosen.add(a) else { chosen.removeAt(0); chosen.add(a) }
                    })
                }
            }
            PrimaryButton("Start", { practising = true }, enabled = chosen.size >= 2)
        }
        return
    }

    val current = chosen[shifts % chosen.size]
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Put your attention on:", style = MaterialTheme.typography.titleMedium, color = c.textDim)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize().scale(pulse.value)) {
                val r = size.minDimension * 0.42f
                if (c.glow.alpha > 0f) drawCircle(Brush.radialGradient(listOf(c.glow.copy(alpha = 0.18f), Color.Transparent), center, r * 1.2f), r * 1.2f)
                drawCircle(c.accent.copy(alpha = 0.5f), r, style = Stroke(1.5.dp.toPx()))
            }
            Text(
                current,
                style = MaterialTheme.typography.displayMedium,
                color = c.text,
                modifier = Modifier.padding(32.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (!env.minimal) {
            BodyText(
                if (shifts < 9) "Stay with it for a moment. When you're ready, shift. If attention wanders, notice where it went and shift again — that return is the practice."
                else "That's plenty of practice. Keep going or finish.",
            )
        }
        Beads(total = 9, done = shifts.coerceAtMost(9), modifier = Modifier.align(Alignment.CenterHorizontally))
        PrimaryButton("Shift attention", { haptics.tick(); shifts++ })
        GhostButton("I'm done with this", onDone, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

// ---------------------------------------------------------------------------------------------
// PRACTICAL NEXT STEP (behavioural activation): one manageable, concrete action.
// ---------------------------------------------------------------------------------------------

@Composable
fun PracticalNextStepActivity(prefs: UserPrefs, onDone: () -> Unit, onMoveOn: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    var group by rememberSaveable { mutableIntStateOf(-1) }
    var stepText by rememberSaveable { mutableStateOf<String?>(null) }
    var smaller by rememberSaveable { mutableStateOf<String?>(null) }
    var committed by rememberSaveable { mutableStateOf(false) }

    val cardSteps = prefs.supportCard.stepLines()
    val groups = buildList {
        if (cardSteps.isNotEmpty()) add(NextStepContent.Group("From my support card", cardSteps.map { NextStepContent.Step(it, it) }))
        addAll(NextStepContent.groups)
    }

    when {
        committed -> StepColumn {
            Spacer(Modifier.height(20.dp))
            Text("Next:", style = MaterialTheme.typography.titleMedium, color = c.textDim)
            Text(stepText.orEmpty(), style = MaterialTheme.typography.displayMedium, color = c.text)
            if (!env.minimal) BodyText("Go and do just that. It's fine if anxiety comes along with you.")
            Spacer(Modifier.height(12.dp))
            PrimaryButton("I'm going to do it now", onMoveOn)
            SecondaryButton("Choose something else", { committed = false; stepText = null; smaller = null; group = -1 })
            GhostButton("Back to activities", onDone, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        stepText != null -> StepColumn {
            StepTitle(stepText.orEmpty())
            if (!env.minimal) BodyText("Does this feel doable? If not, make it smaller.")
            PrimaryButton("Yes, this one", { committed = true })
            val s = smaller
            if (s != null && s != stepText) {
                SecondaryButton("Make it smaller", { stepText = s; smaller = null })
            }
            GhostButton("Pick another", { stepText = null; smaller = null }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        group >= 0 -> StepColumn {
            StepTitle(groups[group].label)
            groups[group].steps.forEach { st ->
                ChoiceCard(st.text, null, { stepText = st.text; smaller = st.smaller })
            }
            GhostButton("Back", { group = -1 }, modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        else -> StepColumn {
            StepTitle("What kind of next step?")
            if (!env.minimal) BodyText("Small and concrete is the goal — not a whole plan.")
            groups.forEachIndexed { i, g -> ChoiceCard(g.label, null, { group = i }) }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// MAKING ROOM (ACT acceptance). Locate, describe, and make space for a feeling. No breathing.
// ---------------------------------------------------------------------------------------------

@Composable
fun AcceptanceActivity(onDone: () -> Unit) {
    val c = Anchor.colors
    val env = Anchor.env
    val haptics = rememberHaptics()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var spotX by rememberSaveable { mutableFloatStateOf(0.5f) }
    var spotY by rememberSaveable { mutableFloatStateOf(0.38f) }
    var placed by rememberSaveable { mutableStateOf(false) }
    var feelingSize by rememberSaveable { mutableFloatStateOf(0.45f) }
    var room by rememberSaveable { mutableFloatStateOf(0f) }

    val title = when (step) {
        0 -> "Where do you notice the feeling most?"
        1 -> "If it had a size, how big would it be?"
        2 -> "Make some room around it."
        else -> "It can be here while you do your next thing."
    }
    val sub = when (step) {
        0 -> "Tap the outline. Anywhere is fine, including \"all over\"."
        1 -> "Just a rough guess."
        2 -> "Drag outward. You're not pushing it away — just giving it space."
        else -> "You don't have to like it. You don't have to fight it either."
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        StepTitle(title)
        if (!env.minimal) BodyText(sub)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Body outline. Tap to mark where the feeling is." }
                    .pointerInput(step) {
                        if (step == 0) {
                            detectTapGestures { p ->
                                spotX = p.x / size.width
                                spotY = p.y / size.height
                                placed = true
                                haptics.tick()
                            }
                        } else if (step == 2) {
                            detectDragGestures { change, amount ->
                                change.consume()
                                val center = Offset(spotX * size.width, spotY * size.height)
                                val outward = (change.position - center).let { d ->
                                    val len = d.getDistance().coerceAtLeast(1f)
                                    (amount.x * d.x + amount.y * d.y) / len
                                }
                                room = (room + outward / (minOf(size.width, size.height) * 0.6f)).coerceIn(0f, 1f)
                            }
                        }
                    },
            ) {
                val w = size.width
                val h = size.height
                val cx = w / 2
                val unit = minOf(w / 3.2f, h / 6.2f)
                val line = c.outline
                val stroke = Stroke(2.dp.toPx())
                // Simple geometric figure: head, torso, arms, legs.
                drawCircle(line, unit * 0.55f, Offset(cx, h * 0.12f + unit * 0.2f), style = stroke)
                drawRoundRect(line, Offset(cx - unit * 0.85f, h * 0.12f + unit * 0.95f), Size(unit * 1.7f, unit * 2.4f), CornerRadius(unit * 0.5f), style = stroke)
                drawRoundRect(line, Offset(cx - unit * 1.35f, h * 0.12f + unit * 1.05f), Size(unit * 0.38f, unit * 2.1f), CornerRadius(unit * 0.2f), style = stroke)
                drawRoundRect(line, Offset(cx + unit * 0.97f, h * 0.12f + unit * 1.05f), Size(unit * 0.38f, unit * 2.1f), CornerRadius(unit * 0.2f), style = stroke)
                drawRoundRect(line, Offset(cx - unit * 0.7f, h * 0.12f + unit * 3.45f), Size(unit * 0.55f, unit * 2.4f), CornerRadius(unit * 0.25f), style = stroke)
                drawRoundRect(line, Offset(cx + unit * 0.15f, h * 0.12f + unit * 3.45f), Size(unit * 0.55f, unit * 2.4f), CornerRadius(unit * 0.25f), style = stroke)

                if (placed || step > 0) {
                    val center = Offset(spotX * w, spotY * h)
                    val r = unit * (0.25f + feelingSize * 1.1f)
                    drawCircle(Brush.radialGradient(listOf(c.alert.copy(alpha = 0.55f), c.alert.copy(alpha = 0.0f)), center, r * 1.4f), r * 1.4f, center)
                    drawCircle(c.alert.copy(alpha = 0.5f), r * 0.55f, center)
                    if (step >= 2) {
                        val roomR = r * (1.3f + room * 2.4f)
                        drawCircle(c.accent.copy(alpha = 0.10f + 0.12f * room), roomR, center)
                        drawCircle(c.accent.copy(alpha = 0.6f), roomR, center, style = Stroke(1.5.dp.toPx()))
                    }
                }
            }
        }
        when (step) {
            0 -> {
                PrimaryButton(if (placed) "Continue" else "Not sure — continue", { if (!placed) { spotX = 0.5f; spotY = 0.42f }; step = 1 })
            }
            1 -> {
                Slider(
                    value = feelingSize, onValueChange = { feelingSize = it },
                    colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent, inactiveTrackColor = c.surfaceHigh),
                    modifier = Modifier.semantics { contentDescription = "Size of the feeling" },
                )
                PrimaryButton("Continue", { step = 2 })
            }
            2 -> {
                if (env.minimal || env.reducedMotion) {
                    SecondaryButton("Give it more room", { room = (room + 0.25f).coerceAtMost(1f) })
                }
                PrimaryButton(if (room > 0.3f) "Continue" else "Continue anyway", { step = 3 })
            }
            else -> PrimaryButton("I'm done with this", onDone)
        }
    }
}
