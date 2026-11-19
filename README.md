# ANCHOR ONE

An offline Android first-aid tool for panic, anxiety, spiralling thoughts, detachment and overwhelm.

It opens straight to one large control: **I NEED HELP NOW**. There's no sign-in, setup, questionnaire, internet use, advertising, analytics or subscription.

> ANCHOR ONE is a self-help tool. It is not a medical device or an emergency service, and it does not diagnose anything. Its techniques draw on CBT and ACT, but the app itself has not been clinically evaluated.

---

## What happens in the first ten seconds

1. **I NEED HELP NOW** opens five large options:
   - Physical alarm
   - Thought spiral
   - Disconnected
   - Overwhelmed
   - **JUST HELP ME START**, which needs no choice at all.

   An optional chip, *"I can only manage very little"*, tells the engine you have low energy. Nobody has to label what is happening.
2. The four labelled pathways open with one **immediate action** that needs almost no thought, such as "Press your feet into the floor". The whole screen is the button.
3. Next comes an **interactive stabilisation activity**, then a **"What would help next?"** checkpoint. It offers another activity, something different, or moving on. It is never a symptom check.
4. At the end you can **rate activities** if you want to.
5. The **Re-entry Ramp** finishes the session by pointing you at a real next activity.

At every step you can skip, switch, pause or exit (✕). If you exit by accident, the home screen offers *Resume where you left off* for 15 minutes.

## Features

| Area | What's implemented |
|---|---|
| **ANCHOR CORE** (`engine/`) | A pure, deterministic reducer `(state, event, context) → state`. It picks activities using the pathway priority, your ratings, favourites, preferred and disabled categories, your stated energy, session length and your Steady-Self Plan. Every pick comes with a plain-language "Why this?" explanation. It never returns an empty choice and never infers distress. |
| **Five interactive experiences** | **Orbit**: spring-physics light with a trail, plus Ring, Figure-eight and Wave guides that light up as you trace them. **Pattern Restore**: drag-and-snap geometric pieces, with 3 templates × 3 levels. **The Next Object**: 24 real-world prompts, moving from looking, to touch and sound, to engaging. **Flow Field**: a particle flow you shape with your finger, with a static version for reduced motion. **Single Thread**: one tiny task at a time using tap, hold, slide and choice interactions, each with a "Can't right now" alternative. |
| **Toolkit** | **Thought Unhooking** (ACT defusion: "I'm having the thought that…", then swipe the thought aside). **Attention Relocation**. **Practical Next Step**, with a "make it smaller" button. **Making Room** (ACT acceptance: place the feeling, give it a size, make room). **Personal Support Card**. **Quiet Mode**. |
| **STILLPOINT** companion | Uses the phone's own offline text-to-speech, with a text-only fallback. Commands: *Keep guiding me*, *Give me something to do*, *Less talking*, *Switch activity*, *I'm ready to continue my day*. Every line has a full and a short version. A no-repeat rotation means nothing repeats until the pool is used up. An optional screen-off mode runs a foreground service with a time-limited wake lock. |
| **Cognitive Load Engine** | Three density modes: **Normal**, **Simplified** and **Minimal**. You can switch at any time from the session bar. When you choose "Overwhelmed" or low energy, the app offers a simpler screen once. It never switches on its own. |
| **Personalisation** | Density, theme, motion level, haptics, voice options, speaking pace, screen-off audio, session length, pattern detail, preferred and disabled categories, favourites, ratings, support card, plan, emergency number, and history on/off/clear. Everything is stored with DataStore, on the device only, and is excluded from backup. |
| **Safety** | Short guidance on when not to assume it's anxiety. A one-tap emergency button opens the dialler with the local number filled in, worked out from the SIM or network country with no permission. Trusted-contact call and text buttons pre-fill only. **The app never calls or messages anyone by itself.** |
| **Quick access** | A home-screen widget, plus long-press launcher shortcuts: Help now, Just start, Quiet mode, Companion. |
| **Accessibility** | Large touch targets (48–120 dp). Respects the system "remove animations" setting. Reduced-motion versions of everything. Screen-reader labels, live regions, and accessible alternatives for the hold and slide gestures. Edge-to-edge layout that respects system insets. Dark and light themes. |

### Three innovations

1. **Anywhere Tap.** On immediate steps, the entire screen is the button, so shaky hands can't miss it.
2. **Steady-Self Plan.** While calm, you choose up to four activities. *Just help me start* then runs your plan in order, so there's nothing to decide in the moment. Its picks are labelled "Step 2 of the plan you made".
3. **Re-entry Ramp.** Sessions don't end on "well done". You pick what's next, using options you've saved before, and get three graduated, concrete steps. A line from your own support card goes with you. *Set the phone down* closes the app.

## Architecture

```
app/src/main/java/com/anchorone/app/
├── engine/      ANCHOR CORE: model, catalog, selector, session reducer, density advisor, codecs (pure Kotlin)
├── guidance/    STILLPOINT: script library, Companion state machine, TTS Speaker, controller, foreground service
├── content/     Prompt and task libraries (Next Object, Single Thread, Re-entry, Next Step, thoughts)
├── data/        UserPrefs + DataStore repository (pure mapper, unit-tested)
├── ui/          AppViewModel (navigation stack + session; survives rotation and process death), theme, components
│   ├── screens/     Home, Triage, Session, Toolkit, Stillpoint, Settings, Support card, Safety, Quiet, History, Plan
│   └── activities/  The nine activity renderers + shared frame loop
└── widget/      Home-screen widget
```

- Kotlin 2.0, Jetpack Compose (BOM 2024.12), Material 3 (heavily customised), coroutines and StateFlow, DataStore. Room isn't used: the data is a few settings plus a short, capped history list.
- Graphics are drawn with Compose Canvas. Flow Field draws into a half-resolution offscreen bitmap so its trails stay cheap. Frame loops run only while the screen is in the foreground.
- Adding an activity takes one `ActivityId` entry plus one `ActivityHost` branch.
- Permissions: `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK` and `WAKE_LOCK`. These are used only when you turn on screen-off voice guidance. There is **no INTERNET permission**.

## Build

Requirements: JDK 17+ and the Android SDK (platform 35, build-tools 35.0.0).

```bash
./gradlew assembleDebug            # → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest        # JVM unit tests (engine, session state, preferences, companion, content)
./gradlew lintDebug                # Android Lint
./gradlew connectedDebugAndroidTest  # Compose UI flow tests: needs a connected device or emulator
./gradlew assembleRelease          # minified (R8) release build; sign it before installing
```

## Verification status

| Check | Result |
|---|---|
| `assembleDebug` | ✅ builds the debug APK (about 10 MB) |
| `assembleRelease` (R8 minify and resource shrinking) | ✅ builds, about 1.4 MB unsigned |
| `testDebugUnitTest` | ✅ 56 tests, 0 failures: selector (12), session engine (21), preferences/DataStore (9), companion and content (9), pattern generator (5) |
| `lintDebug` | ✅ 0 errors. Warnings are newer dependency versions (deliberately pinned) and widget attributes that only apply on API 31+ |
| `assembleDebugAndroidTest` | ✅ the UI flow tests compile |
| `connectedDebugAndroidTest` | ⚠️ **not run.** The build environment had no emulator (no KVM) and no device. The tests click through home, triage, all four pathways to the end, Just Start with checkpoint and follow-up, every activity, exit and resume, density modes, the companion, support card, safety, plan, history, quiet mode, and light theme with reduced motion. They also save screenshots on the device. |

Because no device was available, the UI hasn't been seen running on hardware yet. Run `connectedDebugAndroidTest`, or install the APK, before relying on it.
