package com.anchorone.app.ui

import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.anchorone.app.AnchorApp
import com.anchorone.app.MainActivity
import com.anchorone.app.data.ThemeMode
import com.anchorone.app.data.MotionLevel
import com.anchorone.app.engine.ActivityId
import com.anchorone.app.engine.Density
import com.anchorone.app.engine.Experience
import com.anchorone.app.engine.Phase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/**
 * End-to-end UI flows on a device or emulator: `./gradlew connectedDebugAndroidTest`.
 * Screenshots are written to the app's external files dir (Android/data/com.anchorone.app/files/screenshots).
 * Note: tests share the app's real preferences file; run on a test device/emulator.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AppFlowsTest {

    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val vm: AppViewModel get() = rule.activity.let { a ->
        androidx.lifecycle.ViewModelProvider(a)[AppViewModel::class.java]
    }
    private val app: AnchorApp get() = rule.activity.application as AnchorApp

    @Before
    fun resetState() {
        // Start every flow from defaults (instrumented tests share the real preferences file).
        runBlocking { app.prefs.update { com.anchorone.app.data.UserPrefs() } }
        rule.runOnUiThread { vm.home() }
        rule.waitForIdle()
    }

    private fun waitText(text: String, substring: Boolean = false, timeout: Long = 8_000) {
        rule.waitUntil(timeout) { rule.onAllNodes(hasText(text, substring = substring)).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun text(text: String, substring: Boolean = false): SemanticsNodeInteraction {
        waitText(text, substring)
        return rule.onAllNodes(hasText(text, substring = substring))[0]
    }

    private fun click(text: String, substring: Boolean = false) {
        waitText(text, substring)
        val node = rule.onAllNodes(hasText(text, substring = substring) and hasClickAction()).let { all ->
            if (all.fetchSemanticsNodes().isNotEmpty()) all[0] else rule.onAllNodes(hasText(text, substring = substring))[0]
        }
        runCatching { node.performScrollTo() }
        node.performClick()
        rule.waitForIdle()
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        val bmp = rule.onRoot().captureToImage().asAndroidBitmap()
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(ctx.getExternalFilesDir(null), "screenshots").apply { mkdirs() }
        File(dir, "$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun setPrefs(transform: (com.anchorone.app.data.UserPrefs) -> com.anchorone.app.data.UserPrefs) {
        runBlocking { app.prefs.update(transform) }
        rule.waitForIdle()
    }

    private fun openHelp() {
        rule.waitUntil(8_000) { rule.onAllNodes(hasContentDescription("Opens help options", substring = true)).fetchSemanticsNodes().isNotEmpty() }
        rule.onNode(hasContentDescription("Opens help options", substring = true)).performClick()
        rule.waitForIdle()
    }

    @Test
    fun homeAndTriage() {
        waitText("One tap. No sign-in. Works offline.")
        shot("01_home")
        openHelp()
        text("What feels closest right now?")
        text("JUST HELP ME START")
        Experience.entries.filter { it != Experience.JUST_START }.forEach { text(it.label) }
        shot("02_triage")
    }

    @Test
    fun everyPathwayRunsToTheEnd() {
        listOf(Experience.PHYSICAL_ALARM, Experience.THOUGHT_SPIRAL, Experience.DISCONNECTED, Experience.OVERWHELMED).forEachIndexed { i, e ->
            openHelp()
            click(e.label)
            // Immediate action: whole screen is the button.
            click("Tap anywhere to continue")
            val s = vm.session.value
            assertNotNull(s)
            assertEquals(Phase.STABILIZE, s!!.phase)
            if (i == 0) shot("03_activity_${s.activity!!.name.lowercase()}")
            click("Move on")
            text("Was this useful?")
            click("Useful")
            click("Done")
            text("What's next for you?")
            if (i == 0) shot("04_reentry")
            click("Back to work or study")
            text("Do only the first two minutes of it.")
            if (i == 0) shot("05_reentry_steps")
            click("Finish")
            waitText("One tap. No sign-in. Works offline.")
            assertNull(vm.session.value)
        }
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().history.size == 4 } }
        val prefs = runBlocking { app.prefs.current() }
        assertTrue("ratings recorded", prefs.ratings.values.any { it > 0 })
    }

    @Test
    fun justStartCheckpointAndFollowUp() {
        openHelp()
        click("JUST HELP ME START")
        val first = vm.session.value!!.activity!!
        assertEquals(1, first.demand)
        shot("06_just_start_${first.name.lowercase()}")
        click("I'm done with this")
        text("What would help next?")
        shot("07_checkpoint")
        click("Another activity")
        assertEquals(Phase.FOLLOW_UP, vm.session.value!!.phase)
        click("Different")
        assertEquals(Phase.FOLLOW_UP, vm.session.value!!.phase)
        click("Pause")
        text("Paused")
        shot("08_paused")
        click("Resume")
        click("Move on")
        click("Skip")
        click("Skip this")
        waitText("One tap. No sign-in. Works offline.")
    }

    @Test
    fun everyActivityRendersAndResponds() {
        ActivityId.entries.forEach { a ->
            rule.runOnUiThread { vm.startSingle(a) }
            rule.waitForIdle()
            when (a) {
                ActivityId.ORBIT -> { text("Drag the light slowly", substring = true); click("Ring"); text("Trace the ring with the light.") }
                ActivityId.PATTERN_RESTORE -> { text("Drag each piece into a matching outline."); click("Steady") }
                ActivityId.NEXT_OBJECT -> { click("Found it"); click("A different prompt") }
                ActivityId.FLOW_FIELD -> text("Move your finger through the light.")
                ActivityId.SINGLE_THREAD -> { click("Done"); text("Hold", substring = false) }
                ActivityId.THOUGHT_UNHOOKING -> {
                    click("I can't cope with this")
                    click("Add “I'm having the thought that…”")
                    text("I'm having the thought that I can't cope with this.")
                    click("Add “I notice…”")
                    shot("10_thought_unhooking")
                    click("Continue")
                    click("Set it aside for me")
                    click("It came back")
                    click("Set it aside for me")
                    click("Continue")
                    text("If this thought were just background noise", substring = true)
                }
                ActivityId.ATTENTION_RELOCATION -> { click("Start"); text("Put your attention on:"); click("Shift attention") }
                ActivityId.PRACTICAL_NEXT_STEP -> {
                    click("Look after my body")
                    click("Drink a full glass of water")
                    click("Make it smaller")
                    text("Take one sip of water")
                    click("Yes, this one")
                    text("Next:")
                }
                ActivityId.ACCEPTANCE_PRACTICE -> {
                    click("Not sure — continue")
                    click("Continue")
                    text("Make some room around it.")
                    shot("11_making_room")
                    click("Continue anyway")
                    click("I'm done with this")
                    text("What would help next?")
                }
            }
            if (a != ActivityId.ACCEPTANCE_PRACTICE && a != ActivityId.THOUGHT_UNHOOKING) shot("09_activity_${a.name.lowercase()}")
            rule.runOnUiThread { vm.exitSession() }
            rule.waitForIdle()
        }
    }

    @Test
    fun exitThenResume() {
        openHelp()
        click(Experience.DISCONNECTED.label)
        click("Tap anywhere to continue")
        val before = vm.session.value!!.activity
        rule.onNode(hasContentDescription("Stop and exit")).performClick()
        click("Resume where you left off")
        assertEquals(before, vm.session.value!!.activity)
    }

    @Test
    fun densityModesAndPersistence() {
        waitText("Toolkit")
        rule.onNode(hasContentDescription("Settings")).performClick()
        click("Minimal")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().density == Density.MINIMAL } }
        rule.runOnUiThread { vm.home() }
        rule.waitForIdle()
        assertTrue(rule.onAllNodes(hasText("Companion")).fetchSemanticsNodes().isEmpty())
        shot("12_home_minimal")
        openHelp()
        text("Tap the button below.")
        shot("13_triage_minimal")
        click("JUST HELP ME START")
        click("Fuller")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().density == Density.NORMAL } }
    }

    @Test
    fun overwhelmedSuggestsSimplerScreen() {
        openHelp()
        click(Experience.OVERWHELMED.label)
        text("Make the screen simpler?")
        click("Yes")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().density == Density.SIMPLIFIED } }
        shot("14_simplified_immediate")
    }

    @Test
    fun companionCommands() {
        setPrefs { it.copy(companionVoice = false) }
        waitText("Companion")
        click("Companion")
        text("Keep guiding me")
        shot("15_stillpoint")
        click("Give me something to do")
        click("Done")
        click("Less talking")
        text("More talking")
        click("I'm ready to continue my day")
        click("Continue to my day")
        text("What's next for you?")
    }

    @Test
    fun supportCardSafetyPlanHistoryQuiet() {
        setPrefs { it.copy(emergencyNumber = "999") }
        waitText("My card")
        click("My card")
        rule.onAllNodes(hasText("Reminders that help me", substring = true))[0].performTextInput("I've got through this before.")
        rule.onAllNodes(hasText("Name"))[0].performTextInput("Sam")
        rule.onAllNodes(hasText("Phone number"))[0].performTextInput("07000000000")
        click("Save card")
        text("I've got through this before.")
        shot("16_support_card")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().supportCard.contactName == "Sam" } }

        rule.runOnUiThread { vm.home() }
        click("Safety & emergency")
        text("Call emergency services (999)")
        text("Call Sam")
        shot("17_safety")

        rule.runOnUiThread { vm.navigate(Screen.PLAN) }
        click("Single Thread")
        click("Pattern Restore")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().plan == listOf(ActivityId.SINGLE_THREAD, ActivityId.PATTERN_RESTORE) } }
        assertTrue(runBlocking { app.prefs.current().planEnabled })
        shot("18_plan")
        rule.runOnUiThread { vm.startSession(Experience.JUST_START) }
        rule.waitForIdle()
        assertEquals(ActivityId.SINGLE_THREAD, vm.session.value!!.activity)
        rule.runOnUiThread { vm.exitSession() }

        rule.runOnUiThread { vm.navigate(Screen.HISTORY) }
        text("Keep history")
        rule.runOnUiThread { vm.navigate(Screen.QUIET) }
        click("Quiet mode")
        rule.waitUntil(5_000) { runBlocking { app.prefs.current().quietMode } }
        rule.runOnUiThread { vm.home() }
        text("Start")
        shot("19_home_quiet")
    }

    @Test
    fun lightThemeReducedMotion() {
        setPrefs { it.copy(theme = ThemeMode.LIGHT, motion = MotionLevel.REDUCED) }
        waitText("One tap. No sign-in. Works offline.")
        shot("20_home_light")
        rule.runOnUiThread { vm.startSingle(ActivityId.FLOW_FIELD) }
        text("Tap to place a stone in the stream.")
        shot("21_flow_reduced_motion")
        rule.runOnUiThread { vm.exitSession(); vm.navigate(Screen.SETTINGS) }
        text("Interface density")
        shot("22_settings_light")
        rule.runOnUiThread { vm.navigate(Screen.TOOLKIT) }
        text("Making room")
        shot("23_toolkit_light")
    }
}
