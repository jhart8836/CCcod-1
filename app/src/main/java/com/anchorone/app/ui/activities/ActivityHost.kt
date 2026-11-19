package com.anchorone.app.ui.activities

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import com.anchorone.app.data.UserPrefs
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.ui.AppViewModel
import com.anchorone.app.ui.components.PrimaryButton
import com.anchorone.app.ui.theme.Anchor
import kotlinx.coroutines.isActive

/**
 * Renders any [ActivityId]. Adding a new experience = add an enum entry + one branch here.
 */
@Composable
fun ActivityHost(
    activity: ActivityId,
    paused: Boolean,
    prefs: UserPrefs,
    vm: AppViewModel,
    onDone: () -> Unit,
    onMoveOn: () -> Unit,
    onSwitchTo: (ActivityId) -> Unit,
) {
    val seed = prefs.sessionCount
    when (activity) {
        ActivityId.ORBIT -> OrbitActivity(paused, onDone)
        ActivityId.PATTERN_RESTORE -> PatternRestoreActivity(paused, prefs.patternLevel, seed, onDone) { level ->
            vm.updatePrefs { it.copy(patternLevel = level) }
        }
        ActivityId.NEXT_OBJECT -> NextObjectActivity(seed, onDone)
        ActivityId.FLOW_FIELD -> FlowFieldActivity(paused, onDone)
        ActivityId.SINGLE_THREAD -> SingleThreadActivity(seed, onDone, onMoveOn)
        ActivityId.THOUGHT_UNHOOKING -> ThoughtUnhookingActivity(onDone, onSwitchTo)
        ActivityId.ATTENTION_RELOCATION -> AttentionRelocationActivity(seed, onDone)
        ActivityId.PRACTICAL_NEXT_STEP -> PracticalNextStepActivity(prefs, onDone, onMoveOn)
        ActivityId.ACCEPTANCE_PRACTICE -> AcceptanceActivity(onDone)
    }
}

/**
 * Frame loop that only runs while [running] and the app is in the foreground —
 * so animations never burn battery in the background. Returns a frame counter the
 * Canvas reads to redraw.
 */
@Composable
fun rememberFrameLoop(running: Boolean, onFrame: (dtSeconds: Float, timeSeconds: Float) -> Unit): State<Long> {
    val frame = remember { mutableLongStateOf(0L) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val lifeState by lifecycle.currentStateAsState()
    val active = running && lifeState.isAtLeast(Lifecycle.State.RESUMED)
    val callback by rememberUpdatedState(onFrame)
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        var last = 0L
        var time = 0f
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else ((now - last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                last = now
                time += dt
                callback(dt, time)
                frame.longValue = frame.longValue + 1
            }
        }
    }
    return frame
}

/** Shared layout: instruction at top, interactive area, one clear finishing action. */
@Composable
fun ActivityFrame(
    instruction: String,
    onDone: (() -> Unit)?,
    modifier: Modifier = Modifier,
    doneLabel: String = "I'm done with this",
    sub: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = Anchor.colors
    val env = Anchor.env
    Column(modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = instruction,
            transitionSpec = {
                if (env.reducedMotion) fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                else fadeIn(tween(400)) togetherWith fadeOut(tween(200))
            },
            label = "instruction",
        ) { text ->
            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                Text(
                    text,
                    style = if (env.simplified) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.headlineSmall,
                    color = c.text,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
                if (sub != null && !env.minimal) {
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = c.textDim, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        Box(Modifier.weight(1f).fillMaxWidth(), content = content)
        if (onDone != null) {
            PrimaryButton(
                if (env.simplified) "Done" else doneLabel,
                onDone,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp).heightIn(min = 56.dp),
            )
        }
    }
}
