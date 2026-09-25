# Settings Overhaul Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the one-page settings card grid with a paged rail + detail settings screen, add user keybinds (one extra button per action) and an Advanced page (pre-buffer delay, countdown, diagnostic logs, restore defaults).

**Architecture:** Two new pure-Kotlin units carry all logic and are unit tested: `Keybinds` (key model, resolution, capture validation, persistence format) and `SettingsNavigator` (a reducer from key presses to new `SettingsState` plus an optional `SettingsCommand`). `PlayerViewModel` keeps settings state inside `PlayerUiState`, runs every raw key through `Keybinds.resolve`, delegates settings keys to the navigator and executes the commands. Compose files only draw state.

**Tech Stack:** Kotlin 2.2, Jetpack Compose (material3 + tv), Media3, DataStore Preferences, JUnit 4 + kotlin-test. Android TV / Fire TV, minSdk 21.

**Spec:** `docs/superpowers/specs/2026-09-25-settings-overhaul-design.md` (mockup: `.superpowers/brainstorm/2745416-1790324706/content/settings-overhaul-3.html`)

## Global Constraints

- No emoji anywhere in the UI; icons are Material Symbols (rounded) vector drawables in `res/drawable`, no `material-icons-extended` dependency.
- Keybinds add alongside defaults: defaults always keep working; at most one extra button per action.
- Reserved buttons (never bindable): D-pad up/down/left/right/center, Enter, numpad Enter, Back, 0–9, volume up/down/mute, power, home.
- Countdown "Auto" = 5 s with pre-buffering on, 3 s off.
- Pre-buffer delay options 0.5 / 1 / 2 s, default 1 s. Countdown options Auto / 3 / 5 / 7 s, default Auto.
- Diagnostic logs row exists only in the `sentry` telemetry flavor; the `noSentry` flavor must still send nothing.
- Update row is hidden when `BuildConfig.SELF_UPDATE_ENABLED` is false.
- Restore defaults resets delay, countdown, diagnostics and all keybinds — not bandwidth, pre-buffer on/off or the account.
- Strings exist in both `values/strings.xml` (English) and `values-de/strings.xml` (German).
- Commit messages: plain imperative sentence, no prefix, no `Co-Authored-By` or AI-attribution trailer.
- Tests run with: `./gradlew testSentryDirectDebugUnitTest --tests '<pattern>'` (unit tests cannot call Android framework methods — `android.view.KeyEvent` *constants* are fine, its methods are not).

## Review Focus

- A corrupted or outdated `keybinds` preference containing a reserved or already-used button (e.g. Back) must never hijack that button — parsing drops such entries (Task 1, test `parse drops reserved, default and duplicate keys`).
- Holding OK while the capture dialog is open sends MENU (MainActivity's long-press); that must be refused as a conflict, not silently bound (Task 1, test `the long-press MENU is refused as belonging to Quick menu`).
- In store builds the App page has no focusable rows; Right/OK on the rail must not move focus into an empty pane (Task 3, test `a page without actionable rows cannot be entered`).
- A stored delay/countdown value not in the option list (e.g. from a future version) must preselect the default in the picker, not crash on index -1 (Task 3, test `an unknown stored value preselects the default option`).
- Volume keys pressed while Settings is open must still reach the system (navigator reports them unhandled), and a row index left over from a longer page must not point past the end of a shorter one (Task 3, tests `unrelated keys are left for the system` and `switching pages resets the row`).

---

### Task 1: Keybinds model

**Files:**
- Create: `app/src/main/java/app/tellyfin/androidtv/ui/player/Keybinds.kt`
- Test: `app/src/test/java/app/tellyfin/androidtv/ui/player/KeybindsTest.kt`

**Interfaces:**
- Produces:
  - `enum class KeyAction(val defaultKeyCode: Int)` with entries `QUICK_MENU, CHANNEL_INFO, LIVE_GUIDE, SEARCH, CHANNEL_UP, CHANNEL_DOWN` (in that order — the UI and navigator list them in this order).
  - `sealed interface CaptureResult { data object Accepted; data object Reserved; data class Conflict(val action: KeyAction) }`
  - `data class Keybinds(val extras: Map<KeyAction, Int> = emptyMap())` with `fun resolve(keyCode: Int): Int`, `fun validate(action: KeyAction, keyCode: Int): CaptureResult`, `fun withExtra(action: KeyAction, keyCode: Int?): Keybinds`, `fun serialize(): String`, `companion fun parse(raw: String?): Keybinds`.
  - `val RESERVED_KEYS: Set<Int>`, `fun keyLabel(keyCode: Int): String`.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tellyfin.androidtv.ui.player

import android.view.KeyEvent
import kotlin.test.assertEquals
import org.junit.Test

class KeybindsTest {

    private val red = KeyEvent.KEYCODE_PROG_RED
    private val yellow = KeyEvent.KEYCODE_PROG_YELLOW

    @Test
    fun `an added button stands in for its action's standard button`() {
        val keybinds = Keybinds().withExtra(KeyAction.LIVE_GUIDE, red)
        assertEquals(KeyEvent.KEYCODE_GUIDE, keybinds.resolve(red))
    }

    @Test
    fun `unbound buttons pass through unchanged`() {
        val keybinds = Keybinds().withExtra(KeyAction.LIVE_GUIDE, red)
        assertEquals(yellow, keybinds.resolve(yellow))
        assertEquals(KeyEvent.KEYCODE_GUIDE, keybinds.resolve(KeyEvent.KEYCODE_GUIDE))
    }

    @Test
    fun `navigation, back, digits, volume and power can never be bound`() {
        val keybinds = Keybinds()
        listOf(
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_BACK, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME
        ).forEach { key ->
            assertEquals(CaptureResult.Reserved, keybinds.validate(KeyAction.LIVE_GUIDE, key), "keycode $key")
        }
    }

    @Test
    fun `another action's standard button is refused`() {
        assertEquals(
            CaptureResult.Conflict(KeyAction.CHANNEL_INFO),
            Keybinds().validate(KeyAction.LIVE_GUIDE, KeyEvent.KEYCODE_INFO)
        )
    }

    @Test
    fun `the long-press MENU is refused as belonging to Quick menu`() {
        assertEquals(
            CaptureResult.Conflict(KeyAction.QUICK_MENU),
            Keybinds().validate(KeyAction.SEARCH, KeyEvent.KEYCODE_MENU)
        )
    }

    @Test
    fun `another action's added button is refused`() {
        val keybinds = Keybinds().withExtra(KeyAction.SEARCH, yellow)
        assertEquals(CaptureResult.Conflict(KeyAction.SEARCH), keybinds.validate(KeyAction.LIVE_GUIDE, yellow))
    }

    @Test
    fun `re-capturing an action's own added button is fine`() {
        val keybinds = Keybinds().withExtra(KeyAction.LIVE_GUIDE, red)
        assertEquals(CaptureResult.Accepted, keybinds.validate(KeyAction.LIVE_GUIDE, red))
    }

    @Test
    fun `a new added button replaces the old one and null removes it`() {
        val keybinds = Keybinds().withExtra(KeyAction.LIVE_GUIDE, red).withExtra(KeyAction.LIVE_GUIDE, yellow)
        assertEquals(mapOf(KeyAction.LIVE_GUIDE to yellow), keybinds.extras)
        assertEquals(emptyMap(), keybinds.withExtra(KeyAction.LIVE_GUIDE, null).extras)
    }

    @Test
    fun `serialize and parse round-trip`() {
        val keybinds = Keybinds().withExtra(KeyAction.LIVE_GUIDE, red).withExtra(KeyAction.SEARCH, yellow)
        assertEquals(keybinds, Keybinds.parse(keybinds.serialize()))
    }

    @Test
    fun `parse ignores garbage`() {
        assertEquals(Keybinds(), Keybinds.parse(null))
        assertEquals(Keybinds(), Keybinds.parse(""))
        assertEquals(
            Keybinds().withExtra(KeyAction.SEARCH, yellow),
            Keybinds.parse("NOPE=5;SEARCH=$yellow;LIVE_GUIDE=abc;broken")
        )
    }

    @Test
    fun `parse drops reserved, default and duplicate keys`() {
        val parsed = Keybinds.parse(
            "QUICK_MENU=${KeyEvent.KEYCODE_BACK};CHANNEL_INFO=${KeyEvent.KEYCODE_GUIDE};SEARCH=$red;LIVE_GUIDE=$red"
        )
        assertEquals(mapOf(KeyAction.SEARCH to red), parsed.extras)
        assertEquals(KeyEvent.KEYCODE_BACK, parsed.resolve(KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun `known buttons get short labels`() {
        assertEquals("RED", keyLabel(red))
        assertEquals("CH+", keyLabel(KeyEvent.KEYCODE_CHANNEL_UP))
        assertEquals("GUIDE", keyLabel(KeyEvent.KEYCODE_GUIDE))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*KeybindsTest' -q`
Expected: compile failure, `Unresolved reference 'Keybinds'`.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.tellyfin.androidtv.ui.player

import android.view.KeyEvent

/**
 * Remote actions that can take one extra, user-chosen button on top of their standard one
 * (for remotes that lack a button or send an unusual keycode). Declaration order is the order
 * Settings lists them in.
 */
enum class KeyAction(val defaultKeyCode: Int) {
    QUICK_MENU(KeyEvent.KEYCODE_MENU),
    CHANNEL_INFO(KeyEvent.KEYCODE_INFO),
    LIVE_GUIDE(KeyEvent.KEYCODE_GUIDE),
    SEARCH(KeyEvent.KEYCODE_SEARCH),
    CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP),
    CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN)
}

sealed interface CaptureResult {
    data object Accepted : CaptureResult
    data object Reserved : CaptureResult
    data class Conflict(val action: KeyAction) : CaptureResult
}

/** Buttons the app depends on (navigation, digits) or the system owns (volume, power, home). */
val RESERVED_KEYS: Set<Int> = setOf(
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME
) + (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)

data class Keybinds(val extras: Map<KeyAction, Int> = emptyMap()) {

    /** A user-added button becomes its action's standard keycode; anything else passes through. */
    fun resolve(keyCode: Int): Int =
        extras.entries.firstOrNull { it.value == keyCode }?.key?.defaultKeyCode ?: keyCode

    fun validate(action: KeyAction, keyCode: Int): CaptureResult {
        if (keyCode in RESERVED_KEYS) return CaptureResult.Reserved
        val owner = KeyAction.entries.firstOrNull { it.defaultKeyCode == keyCode }
            ?: extras.entries.firstOrNull { it.value == keyCode && it.key != action }?.key
        return if (owner != null) CaptureResult.Conflict(owner) else CaptureResult.Accepted
    }

    fun withExtra(action: KeyAction, keyCode: Int?): Keybinds =
        Keybinds(if (keyCode == null) extras - action else extras + (action to keyCode))

    fun serialize(): String = extras.entries.joinToString(";") { "${it.key.name}=${it.value}" }

    companion object {
        /**
         * Lenient on purpose: whatever is stored (older version, hand-edited, corrupt) must never
         * take over a reserved button or one that already belongs to something else.
         */
        fun parse(raw: String?): Keybinds {
            if (raw.isNullOrBlank()) return Keybinds()
            return raw.split(';').fold(Keybinds()) { keybinds, entry ->
                val parts = entry.split('=')
                val action = KeyAction.entries.firstOrNull { it.name == parts.getOrNull(0) }
                val keyCode = parts.getOrNull(1)?.toIntOrNull()
                if (parts.size == 2 && action != null && keyCode != null &&
                    keybinds.validate(action, keyCode) == CaptureResult.Accepted
                ) keybinds.withExtra(action, keyCode) else keybinds
            }
        }
    }
}

private val KEY_LABELS = mapOf(
    KeyEvent.KEYCODE_MENU to "MENU",
    KeyEvent.KEYCODE_INFO to "INFO",
    KeyEvent.KEYCODE_GUIDE to "GUIDE",
    KeyEvent.KEYCODE_SEARCH to "SEARCH",
    KeyEvent.KEYCODE_CHANNEL_UP to "CH+",
    KeyEvent.KEYCODE_CHANNEL_DOWN to "CH−",
    KeyEvent.KEYCODE_PROG_RED to "RED",
    KeyEvent.KEYCODE_PROG_GREEN to "GREEN",
    KeyEvent.KEYCODE_PROG_YELLOW to "YELLOW",
    KeyEvent.KEYCODE_PROG_BLUE to "BLUE",
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "PLAY/PAUSE",
    KeyEvent.KEYCODE_MEDIA_PLAY to "PLAY",
    KeyEvent.KEYCODE_MEDIA_PAUSE to "PAUSE",
    KeyEvent.KEYCODE_MEDIA_STOP to "STOP",
    KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "FF",
    KeyEvent.KEYCODE_MEDIA_REWIND to "REW",
    KeyEvent.KEYCODE_MEDIA_NEXT to "NEXT",
    KeyEvent.KEYCODE_MEDIA_PREVIOUS to "PREV",
    KeyEvent.KEYCODE_MEDIA_RECORD to "REC",
    KeyEvent.KEYCODE_BOOKMARK to "BOOKMARK",
    KeyEvent.KEYCODE_TV_INPUT to "INPUT",
    KeyEvent.KEYCODE_CAPTIONS to "CC",
    KeyEvent.KEYCODE_LAST_CHANNEL to "LAST",
    KeyEvent.KEYCODE_DVR to "DVR",
    KeyEvent.KEYCODE_SETTINGS to "SETTINGS"
)

/** Short, remote-style name for a button, e.g. "RED" or "CH+". */
fun keyLabel(keyCode: Int): String = KEY_LABELS[keyCode]
    ?: KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_").replace('_', ' ')
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*KeybindsTest' -q`
Expected: PASS (no output besides Gradle warnings).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/Keybinds.kt app/src/test/java/app/tellyfin/androidtv/ui/player/KeybindsTest.kt
git commit -m "Add a keybind model for extra remote buttons"
```

---

### Task 2: Configurable pre-buffer delay and countdown

**Files:**
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/Prebuffer.kt` (the `object Prebuffer` block)
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerViewModel.kt` (one line in `previewChannel`: `delay(Prebuffer.START_DELAY_MS)`)
- Test: `app/src/test/java/app/tellyfin/androidtv/ui/player/PrebufferTest.kt` (first test)

**Interfaces:**
- Produces: `Prebuffer.DEFAULT_START_DELAY_MS: Long` (1000), `Prebuffer.COUNTDOWN_AUTO: Long` (0), `Prebuffer.START_DELAY_OPTIONS: List<Long>` (500, 1000, 2000), `Prebuffer.COUNTDOWN_OPTIONS: List<Long>` (0, 3000, 5000, 7000), `Prebuffer.countdownMs(enabled: Boolean, setting: Long = COUNTDOWN_AUTO): Long`. `Prebuffer.START_DELAY_MS` is removed.

- [ ] **Step 1: Write the failing test** — replace the first test in `PrebufferTest.kt` (the one named ``countdown is longer with prebuffering so there is time to preload after the reading pause``) with:

```kotlin
    @Test
    fun `auto countdown is longer with prebuffering so there is time to preload after the reading pause`() {
        assertEquals(5_000L, Prebuffer.countdownMs(enabled = true))
        assertEquals(3_000L, Prebuffer.countdownMs(enabled = false))
        assertEquals(1_000L, Prebuffer.DEFAULT_START_DELAY_MS)
    }

    @Test
    fun `an explicit countdown setting wins over auto either way`() {
        assertEquals(7_000L, Prebuffer.countdownMs(enabled = true, setting = 7_000L))
        assertEquals(7_000L, Prebuffer.countdownMs(enabled = false, setting = 7_000L))
        assertEquals(5_000L, Prebuffer.countdownMs(enabled = true, setting = Prebuffer.COUNTDOWN_AUTO))
    }

    @Test
    fun `the defaults are among the offered options`() {
        assertEquals(listOf(500L, 1_000L, 2_000L), Prebuffer.START_DELAY_OPTIONS)
        assertEquals(listOf(Prebuffer.COUNTDOWN_AUTO, 3_000L, 5_000L, 7_000L), Prebuffer.COUNTDOWN_OPTIONS)
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*PrebufferTest' -q`
Expected: compile failure, `Unresolved reference 'DEFAULT_START_DELAY_MS'`.

- [ ] **Step 3: Implement** — replace the whole `object Prebuffer { ... }` block (and its KDoc) in `Prebuffer.kt` with:

```kotlin
/**
 * Timing for the channel-switch preview banner. With pre-buffering on, the countdown gets two
 * extra seconds: the first part (the start delay) is for reading the banner — flicking past a
 * channel never touches a tuner — the rest is spent loading the stream so the switch starts
 * instantly. Jellyfin can take several seconds to open a tuner, so loading gets the lion's share.
 * Both are user-adjustable under Settings → Advanced.
 */
object Prebuffer {
    const val DEFAULT_START_DELAY_MS = 1_000L
    /** Countdown setting meaning "5 s with pre-buffering, 3 s without". */
    const val COUNTDOWN_AUTO = 0L
    val START_DELAY_OPTIONS = listOf(500L, DEFAULT_START_DELAY_MS, 2_000L)
    val COUNTDOWN_OPTIONS = listOf(COUNTDOWN_AUTO, 3_000L, 5_000L, 7_000L)
    private const val COUNTDOWN_MS = 3_000L
    private const val COUNTDOWN_WITH_PREBUFFER_MS = 5_000L

    fun countdownMs(enabled: Boolean, setting: Long = COUNTDOWN_AUTO): Long = when {
        setting != COUNTDOWN_AUTO -> setting
        enabled -> COUNTDOWN_WITH_PREBUFFER_MS
        else -> COUNTDOWN_MS
    }
}
```

In `PlayerViewModel.kt`, inside `previewChannel`, change `delay(Prebuffer.START_DELAY_MS)` to `delay(Prebuffer.DEFAULT_START_DELAY_MS)` (Task 5 swaps it for the user setting).

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*PrebufferTest' -q && ./gradlew assembleSentryDirectDebug -q`
Expected: PASS, build succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/Prebuffer.kt app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerViewModel.kt app/src/test/java/app/tellyfin/androidtv/ui/player/PrebufferTest.kt
git commit -m "Make the pre-buffer delay and countdown configurable values"
```

---

### Task 3: Settings navigator

**Files:**
- Create: `app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsNavigator.kt`
- Test: `app/src/test/java/app/tellyfin/androidtv/ui/player/SettingsNavigatorTest.kt`

**Interfaces:**
- Consumes: `KeyAction`, `Keybinds`, `CaptureResult` (Task 1); `Prebuffer.START_DELAY_OPTIONS`, `COUNTDOWN_OPTIONS`, `DEFAULT_START_DELAY_MS`, `COUNTDOWN_AUTO` (Task 2); existing top-level `BITRATE_OPTIONS: List<Pair<Int?, String>>` in `PlayerViewModel.kt`.
- Produces (all top-level in package `app.tellyfin.androidtv.ui.player`):
  - `enum class SettingsPage { STREAMING, KEYBINDS, APP, ACCOUNT, ADVANCED }`
  - `sealed interface SettingsRow` with `data object Bandwidth, PrebufferToggle, Update, SignOut, PrebufferDelay, Countdown, Diagnostics, RestoreDefaults` and `data class Keybind(val action: KeyAction)`
  - `enum class PickerKind { BANDWIDTH, PREBUFFER_DELAY, COUNTDOWN }`, `data class PickerState(val kind: PickerKind, val index: Int)`
  - `sealed interface CaptureMessage { data object Reserved; data class Conflict(val keyCode: Int, val owner: KeyAction) }`, `data class CaptureState(val action: KeyAction, val message: CaptureMessage? = null)`
  - `data class SettingsState(val page: SettingsPage = STREAMING, val inPane: Boolean = false, val row: Int = 0, val picker: PickerState? = null, val capture: CaptureState? = null)`
  - `data class SettingsContext(val selfUpdateEnabled: Boolean, val diagnosticsAvailable: Boolean)`
  - `data class SettingsValues(val maxBitrate: Int?, val prebufferDelayMs: Long, val countdownSettingMs: Long, val keybinds: Keybinds)`
  - `sealed interface SettingsCommand` with `data object Close, TogglePrebuffer, ToggleDiagnostics, ActivateUpdate, SignOut, RestoreDefaults` and `data class PickBitrate(val bitrate: Int?)`, `PickPrebufferDelay(val ms: Long)`, `PickCountdown(val ms: Long)`, `SetExtraKey(val action: KeyAction, val keyCode: Int?)`
  - `data class SettingsResult(val state: SettingsState, val command: SettingsCommand? = null, val handled: Boolean = true)`
  - `fun settingsRows(page: SettingsPage, context: SettingsContext): List<SettingsRow>`
  - `object SettingsNavigator { fun onKey(state: SettingsState, keyCode: Int, context: SettingsContext, values: SettingsValues): SettingsResult }`

- [ ] **Step 1: Write the failing test**

```kotlin
package app.tellyfin.androidtv.ui.player

import android.view.KeyEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class SettingsNavigatorTest {

    private val full = SettingsContext(selfUpdateEnabled = true, diagnosticsAvailable = true)
    private val store = SettingsContext(selfUpdateEnabled = false, diagnosticsAvailable = false)
    private val values = SettingsValues(
        maxBitrate = 8_000_000,
        prebufferDelayMs = Prebuffer.DEFAULT_START_DELAY_MS,
        countdownSettingMs = Prebuffer.COUNTDOWN_AUTO,
        keybinds = Keybinds()
    )

    private fun press(
        state: SettingsState,
        vararg keys: Int,
        context: SettingsContext = full,
        values: SettingsValues = this.values
    ): SettingsResult {
        var result = SettingsResult(state)
        for (key in keys) result = SettingsNavigator.onKey(result.state, key, context, values)
        return result
    }

    private val up = KeyEvent.KEYCODE_DPAD_UP
    private val down = KeyEvent.KEYCODE_DPAD_DOWN
    private val left = KeyEvent.KEYCODE_DPAD_LEFT
    private val right = KeyEvent.KEYCODE_DPAD_RIGHT
    private val ok = KeyEvent.KEYCODE_DPAD_CENTER
    private val back = KeyEvent.KEYCODE_BACK

    @Test
    fun `settings open on the rail at Streaming`() {
        val state = SettingsState()
        assertEquals(SettingsPage.STREAMING, state.page)
        assertFalse(state.inPane)
    }

    @Test
    fun `up and down on the rail change page and stop at the ends`() {
        assertEquals(SettingsPage.KEYBINDS, press(SettingsState(), down).state.page)
        assertEquals(SettingsPage.STREAMING, press(SettingsState(), up).state.page)
        assertEquals(SettingsPage.ADVANCED, press(SettingsState(), down, down, down, down, down, down).state.page)
    }

    @Test
    fun `right or OK enters the page, left or back returns to the rail`() {
        val inPane = press(SettingsState(), right).state
        assertTrue(inPane.inPane)
        assertEquals(0, inPane.row)
        assertTrue(press(SettingsState(), ok).state.inPane)
        assertFalse(press(inPane, left).state.inPane)
        assertFalse(press(inPane, back).state.inPane)
    }

    @Test
    fun `back on the rail closes settings`() {
        assertEquals(SettingsCommand.Close, press(SettingsState(), back).command)
    }

    @Test
    fun `rows stop at the first and last instead of wrapping`() {
        val keybindsPane = SettingsState(page = SettingsPage.KEYBINDS, inPane = true)
        assertEquals(0, press(keybindsPane, up).state.row)
        assertEquals(5, press(keybindsPane, down, down, down, down, down, down, down).state.row)
    }

    @Test
    fun `switching pages resets the row`() {
        val result = press(SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 5), left, up, right)
        assertEquals(SettingsPage.STREAMING, result.state.page)
        assertEquals(0, result.state.row)
    }

    @Test
    fun `a page without actionable rows cannot be entered`() {
        val app = SettingsState(page = SettingsPage.APP)
        assertFalse(press(app, right, context = store).state.inPane)
        assertFalse(press(app, ok, context = store).state.inPane)
        assertTrue(press(app, right, context = full).state.inPane)
    }

    @Test
    fun `advanced page hides diagnostics where the build can't send them`() {
        assertEquals(
            listOf(SettingsRow.PrebufferDelay, SettingsRow.Countdown, SettingsRow.Diagnostics, SettingsRow.RestoreDefaults),
            settingsRows(SettingsPage.ADVANCED, full)
        )
        assertEquals(
            listOf(SettingsRow.PrebufferDelay, SettingsRow.Countdown, SettingsRow.RestoreDefaults),
            settingsRows(SettingsPage.ADVANCED, store)
        )
    }

    @Test
    fun `keybind rows follow the action order`() {
        assertEquals(KeyAction.entries.map { SettingsRow.Keybind(it) }, settingsRows(SettingsPage.KEYBINDS, full))
    }

    @Test
    fun `OK on a toggle row emits its command`() {
        val streaming = SettingsState(inPane = true, row = 1)
        assertEquals(SettingsCommand.TogglePrebuffer, press(streaming, ok).command)
    }

    @Test
    fun `bandwidth picker preselects the current value and emits the picked one`() {
        val opened = press(SettingsState(inPane = true, row = 0), ok).state
        val currentIndex = BITRATE_OPTIONS.indexOfFirst { it.first == 8_000_000 }
        assertEquals(PickerState(PickerKind.BANDWIDTH, currentIndex), opened.picker)

        val picked = press(opened, down, ok)
        assertNull(picked.state.picker)
        assertEquals(SettingsCommand.PickBitrate(BITRATE_OPTIONS[currentIndex + 1].first), picked.command)
    }

    @Test
    fun `back closes a picker without changing anything`() {
        val opened = press(SettingsState(inPane = true, row = 0), ok).state
        val cancelled = press(opened, down, back)
        assertNull(cancelled.state.picker)
        assertNull(cancelled.command)
        assertTrue(cancelled.state.inPane)
    }

    @Test
    fun `countdown picker offers auto first and emits milliseconds`() {
        val advanced = SettingsState(page = SettingsPage.ADVANCED, inPane = true, row = 1)
        val opened = press(advanced, ok).state
        assertEquals(PickerState(PickerKind.COUNTDOWN, 0), opened.picker)
        assertEquals(SettingsCommand.PickCountdown(7_000L), press(opened, up, ok).command)
    }

    @Test
    fun `an unknown stored value preselects the default option`() {
        val odd = values.copy(prebufferDelayMs = 4_000L, countdownSettingMs = 9_000L)
        val delay = press(SettingsState(page = SettingsPage.ADVANCED, inPane = true, row = 0), ok, values = odd)
        assertEquals(PickerState(PickerKind.PREBUFFER_DELAY, 1), delay.state.picker)
        val countdown = press(SettingsState(page = SettingsPage.ADVANCED, inPane = true, row = 1), ok, values = odd)
        assertEquals(PickerState(PickerKind.COUNTDOWN, 0), countdown.state.picker)
    }

    @Test
    fun `OK on a keybind row opens capture for that action`() {
        val guide = SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 2)
        assertEquals(CaptureState(KeyAction.LIVE_GUIDE), press(guide, ok).state.capture)
    }

    @Test
    fun `capturing a free button saves it and closes`() {
        val capturing = SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 2, capture = CaptureState(KeyAction.LIVE_GUIDE))
        val result = press(capturing, KeyEvent.KEYCODE_PROG_RED)
        assertNull(result.state.capture)
        assertEquals(SettingsCommand.SetExtraKey(KeyAction.LIVE_GUIDE, KeyEvent.KEYCODE_PROG_RED), result.command)
    }

    @Test
    fun `capturing a taken or reserved button explains why and stays open`() {
        val capturing = SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 2, capture = CaptureState(KeyAction.LIVE_GUIDE))
        val taken = press(capturing, KeyEvent.KEYCODE_INFO)
        assertEquals(CaptureMessage.Conflict(KeyEvent.KEYCODE_INFO, KeyAction.CHANNEL_INFO), taken.state.capture?.message)
        assertNull(taken.command)
        val reserved = press(capturing, up)
        assertEquals(CaptureMessage.Reserved, reserved.state.capture?.message)
    }

    @Test
    fun `OK in capture removes the added button, back just cancels`() {
        val capturing = SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 2, capture = CaptureState(KeyAction.LIVE_GUIDE))
        val removed = press(capturing, ok)
        assertNull(removed.state.capture)
        assertEquals(SettingsCommand.SetExtraKey(KeyAction.LIVE_GUIDE, null), removed.command)
        val cancelled = press(capturing, back)
        assertNull(cancelled.state.capture)
        assertNull(cancelled.command)
    }

    @Test
    fun `unrelated keys are left for the system`() {
        assertFalse(press(SettingsState(), KeyEvent.KEYCODE_VOLUME_UP).handled)
        assertFalse(press(SettingsState(inPane = true), KeyEvent.KEYCODE_VOLUME_DOWN).handled)
        val picker = SettingsState(inPane = true, picker = PickerState(PickerKind.BANDWIDTH, 0))
        assertFalse(press(picker, KeyEvent.KEYCODE_VOLUME_UP).handled)
    }

    @Test
    fun `action rows emit their commands`() {
        assertEquals(SettingsCommand.SignOut, press(SettingsState(page = SettingsPage.ACCOUNT, inPane = true), ok).command)
        assertEquals(SettingsCommand.ActivateUpdate, press(SettingsState(page = SettingsPage.APP, inPane = true), ok).command)
        assertEquals(
            SettingsCommand.RestoreDefaults,
            press(SettingsState(page = SettingsPage.ADVANCED, inPane = true, row = 3), ok).command
        )
        assertEquals(
            SettingsCommand.ToggleDiagnostics,
            press(SettingsState(page = SettingsPage.ADVANCED, inPane = true, row = 2), ok).command
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*SettingsNavigatorTest' -q`
Expected: compile failure, `Unresolved reference 'SettingsContext'`.

- [ ] **Step 3: Write the implementation**

```kotlin
package app.tellyfin.androidtv.ui.player

import android.view.KeyEvent

// Pure model + key reducer for the paged Settings screen. PlayerViewModel feeds it key presses
// and executes the returned commands; SettingsScreen only draws SettingsState. Keeping this free
// of Android/Compose calls is what makes it unit-testable.

enum class SettingsPage { STREAMING, KEYBINDS, APP, ACCOUNT, ADVANCED }

/** Focusable rows only — read-only info lines (version, server, …) are drawn but never focused. */
sealed interface SettingsRow {
    data object Bandwidth : SettingsRow
    data object PrebufferToggle : SettingsRow
    data class Keybind(val action: KeyAction) : SettingsRow
    data object Update : SettingsRow
    data object SignOut : SettingsRow
    data object PrebufferDelay : SettingsRow
    data object Countdown : SettingsRow
    data object Diagnostics : SettingsRow
    data object RestoreDefaults : SettingsRow
}

enum class PickerKind { BANDWIDTH, PREBUFFER_DELAY, COUNTDOWN }

data class PickerState(val kind: PickerKind, val index: Int)

sealed interface CaptureMessage {
    data object Reserved : CaptureMessage
    data class Conflict(val keyCode: Int, val owner: KeyAction) : CaptureMessage
}

data class CaptureState(val action: KeyAction, val message: CaptureMessage? = null)

data class SettingsState(
    val page: SettingsPage = SettingsPage.STREAMING,
    val inPane: Boolean = false,
    val row: Int = 0,
    val picker: PickerState? = null,
    val capture: CaptureState? = null
)

/** What differs between builds: store builds have no updater, noSentry builds no diagnostics. */
data class SettingsContext(val selfUpdateEnabled: Boolean, val diagnosticsAvailable: Boolean)

/** Current values, for preselecting picker entries and validating captured buttons. */
data class SettingsValues(
    val maxBitrate: Int?,
    val prebufferDelayMs: Long,
    val countdownSettingMs: Long,
    val keybinds: Keybinds
)

sealed interface SettingsCommand {
    data object Close : SettingsCommand
    data object TogglePrebuffer : SettingsCommand
    data object ToggleDiagnostics : SettingsCommand
    data object ActivateUpdate : SettingsCommand
    data object SignOut : SettingsCommand
    data object RestoreDefaults : SettingsCommand
    data class PickBitrate(val bitrate: Int?) : SettingsCommand
    data class PickPrebufferDelay(val ms: Long) : SettingsCommand
    data class PickCountdown(val ms: Long) : SettingsCommand
    /** keyCode null removes the action's added button. */
    data class SetExtraKey(val action: KeyAction, val keyCode: Int?) : SettingsCommand
}

/** handled = false lets the key fall through to the system (volume and the like). */
data class SettingsResult(
    val state: SettingsState,
    val command: SettingsCommand? = null,
    val handled: Boolean = true
)

fun settingsRows(page: SettingsPage, context: SettingsContext): List<SettingsRow> = when (page) {
    SettingsPage.STREAMING -> listOf(SettingsRow.Bandwidth, SettingsRow.PrebufferToggle)
    SettingsPage.KEYBINDS -> KeyAction.entries.map { SettingsRow.Keybind(it) }
    SettingsPage.APP -> if (context.selfUpdateEnabled) listOf(SettingsRow.Update) else emptyList()
    SettingsPage.ACCOUNT -> listOf(SettingsRow.SignOut)
    SettingsPage.ADVANCED -> buildList {
        add(SettingsRow.PrebufferDelay)
        add(SettingsRow.Countdown)
        if (context.diagnosticsAvailable) add(SettingsRow.Diagnostics)
        add(SettingsRow.RestoreDefaults)
    }
}

private fun optionCount(kind: PickerKind): Int = when (kind) {
    PickerKind.BANDWIDTH -> BITRATE_OPTIONS.size
    PickerKind.PREBUFFER_DELAY -> Prebuffer.START_DELAY_OPTIONS.size
    PickerKind.COUNTDOWN -> Prebuffer.COUNTDOWN_OPTIONS.size
}

object SettingsNavigator {

    fun onKey(state: SettingsState, keyCode: Int, context: SettingsContext, values: SettingsValues): SettingsResult =
        when {
            state.capture != null -> onCaptureKey(state, state.capture, keyCode, values.keybinds)
            state.picker != null -> onPickerKey(state, state.picker, keyCode)
            state.inPane -> onPaneKey(state, keyCode, context, values)
            else -> onRailKey(state, keyCode, context)
        }

    private fun onRailKey(state: SettingsState, keyCode: Int, context: SettingsContext): SettingsResult {
        val pages = SettingsPage.entries
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP ->
                SettingsResult(state.copy(page = pages[(state.page.ordinal - 1).coerceAtLeast(0)], row = 0))
            KeyEvent.KEYCODE_DPAD_DOWN ->
                SettingsResult(state.copy(page = pages[(state.page.ordinal + 1).coerceAtMost(pages.lastIndex)], row = 0))
            KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                // A page with nothing to act on (App in store builds) has no pane focus to take.
                SettingsResult(
                    if (settingsRows(state.page, context).isEmpty()) state
                    else state.copy(inPane = true, row = 0)
                )
            KeyEvent.KEYCODE_DPAD_LEFT -> SettingsResult(state)
            KeyEvent.KEYCODE_BACK -> SettingsResult(state, SettingsCommand.Close)
            else -> SettingsResult(state, handled = false)
        }
    }

    private fun onPaneKey(
        state: SettingsState,
        keyCode: Int,
        context: SettingsContext,
        values: SettingsValues
    ): SettingsResult {
        val rows = settingsRows(state.page, context)
        if (rows.isEmpty()) return onRailKey(state.copy(inPane = false), keyCode, context)
        val row = state.row.coerceIn(0, rows.lastIndex)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> SettingsResult(state.copy(row = (row - 1).coerceAtLeast(0)))
            KeyEvent.KEYCODE_DPAD_DOWN -> SettingsResult(state.copy(row = (row + 1).coerceAtMost(rows.lastIndex)))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> SettingsResult(state.copy(inPane = false, row = row))
            KeyEvent.KEYCODE_DPAD_RIGHT -> SettingsResult(state.copy(row = row))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> activate(state.copy(row = row), rows[row], values)
            else -> SettingsResult(state, handled = false)
        }
    }

    private fun activate(state: SettingsState, row: SettingsRow, values: SettingsValues): SettingsResult {
        fun picker(kind: PickerKind, index: Int) = SettingsResult(state.copy(picker = PickerState(kind, index)))
        return when (row) {
            SettingsRow.Bandwidth -> picker(
                PickerKind.BANDWIDTH,
                BITRATE_OPTIONS.indexOfFirst { it.first == values.maxBitrate }.coerceAtLeast(0)
            )
            SettingsRow.PrebufferDelay -> picker(
                PickerKind.PREBUFFER_DELAY,
                optionIndex(Prebuffer.START_DELAY_OPTIONS, values.prebufferDelayMs, Prebuffer.DEFAULT_START_DELAY_MS)
            )
            SettingsRow.Countdown -> picker(
                PickerKind.COUNTDOWN,
                optionIndex(Prebuffer.COUNTDOWN_OPTIONS, values.countdownSettingMs, Prebuffer.COUNTDOWN_AUTO)
            )
            SettingsRow.PrebufferToggle -> SettingsResult(state, SettingsCommand.TogglePrebuffer)
            SettingsRow.Diagnostics -> SettingsResult(state, SettingsCommand.ToggleDiagnostics)
            is SettingsRow.Keybind -> SettingsResult(state.copy(capture = CaptureState(row.action)))
            SettingsRow.Update -> SettingsResult(state, SettingsCommand.ActivateUpdate)
            SettingsRow.SignOut -> SettingsResult(state, SettingsCommand.SignOut)
            SettingsRow.RestoreDefaults -> SettingsResult(state, SettingsCommand.RestoreDefaults)
        }
    }

    /** A stored value that isn't offered (e.g. written by a newer version) falls back to the default. */
    private fun optionIndex(options: List<Long>, value: Long, default: Long): Int =
        options.indexOf(value).takeIf { it >= 0 } ?: options.indexOf(default)

    private fun onPickerKey(state: SettingsState, picker: PickerState, keyCode: Int): SettingsResult {
        val count = optionCount(picker.kind)
        return when (keyCode) {
            KeyEvent.KEYCODE_DPAD_UP ->
                SettingsResult(state.copy(picker = picker.copy(index = (picker.index - 1 + count) % count)))
            KeyEvent.KEYCODE_DPAD_DOWN ->
                SettingsResult(state.copy(picker = picker.copy(index = (picker.index + 1) % count)))
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
                SettingsResult(state.copy(picker = null), pickCommand(picker))
            KeyEvent.KEYCODE_BACK -> SettingsResult(state.copy(picker = null))
            KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> SettingsResult(state)
            else -> SettingsResult(state, handled = false)
        }
    }

    private fun pickCommand(picker: PickerState): SettingsCommand = when (picker.kind) {
        PickerKind.BANDWIDTH -> SettingsCommand.PickBitrate(BITRATE_OPTIONS[picker.index].first)
        PickerKind.PREBUFFER_DELAY -> SettingsCommand.PickPrebufferDelay(Prebuffer.START_DELAY_OPTIONS[picker.index])
        PickerKind.COUNTDOWN -> SettingsCommand.PickCountdown(Prebuffer.COUNTDOWN_OPTIONS[picker.index])
    }

    /** Every key is consumed here: the dialog is literally asking for "the next button". */
    private fun onCaptureKey(
        state: SettingsState,
        capture: CaptureState,
        keyCode: Int,
        keybinds: Keybinds
    ): SettingsResult = when (keyCode) {
        KeyEvent.KEYCODE_BACK -> SettingsResult(state.copy(capture = null))
        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER ->
            SettingsResult(state.copy(capture = null), SettingsCommand.SetExtraKey(capture.action, null))
        else -> when (val result = keybinds.validate(capture.action, keyCode)) {
            CaptureResult.Accepted ->
                SettingsResult(state.copy(capture = null), SettingsCommand.SetExtraKey(capture.action, keyCode))
            CaptureResult.Reserved ->
                SettingsResult(state.copy(capture = capture.copy(message = CaptureMessage.Reserved)))
            is CaptureResult.Conflict ->
                SettingsResult(state.copy(capture = capture.copy(message = CaptureMessage.Conflict(keyCode, result.action))))
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testSentryDirectDebugUnitTest --tests '*SettingsNavigatorTest' -q`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsNavigator.kt app/src/test/java/app/tellyfin/androidtv/ui/player/SettingsNavigatorTest.kt
git commit -m "Add a key reducer for the paged settings screen"
```

---

### Task 4: Preferences and the diagnostic-logs switch

**Files:**
- Modify: `app/src/main/java/app/tellyfin/androidtv/data/prefs/PreferencesRepository.kt`
- Modify: `app/src/sentry/java/app/tellyfin/androidtv/diagnostics/CrashReporting.kt`
- Modify: `app/src/noSentry/java/app/tellyfin/androidtv/diagnostics/CrashReporting.kt`

**Interfaces:**
- Produces:
  - `PreferencesRepository`: `keybinds: Flow<String?>`, `prebufferDelayMs: Flow<Long?>`, `countdownMs: Flow<Long?>`, `diagnosticsEnabled: Flow<Boolean>`, `suspend fun saveKeybinds(serialized: String)`, `suspend fun savePrebufferDelayMs(ms: Long)`, `suspend fun saveCountdownMs(ms: Long)`, `suspend fun saveDiagnosticsEnabled(enabled: Boolean)`, `suspend fun clearAdvancedSettings()`.
  - `CrashReporting.SUPPORTS_DIAGNOSTICS: Boolean` (const; true in sentry, false in noSentry), `CrashReporting.setDiagnosticsEnabled(enabled: Boolean)`.

No unit tests: DataStore and Sentry need a device; this task is verified by compiling both flavors.

- [ ] **Step 1: Add preference keys, flows and savers** in `PreferencesRepository.kt`.

Add the import next to the other key imports:

```kotlin
import androidx.datastore.preferences.core.longPreferencesKey
```

In `private object Keys`, after `PREBUFFER_AUTO_DISABLED`:

```kotlin
        val KEYBINDS = stringPreferencesKey("keybinds")
        val PREBUFFER_DELAY_MS = longPreferencesKey("prebuffer_delay_ms")
        val COUNTDOWN_MS = longPreferencesKey("countdown_ms")
        val DIAGNOSTICS_ENABLED = booleanPreferencesKey("diagnostics_enabled")
```

After the `prebufferAutoDisabled` flow:

```kotlin
    val keybinds: Flow<String?> = context.dataStore.data.map { it[Keys.KEYBINDS] }
    val prebufferDelayMs: Flow<Long?> = context.dataStore.data.map { it[Keys.PREBUFFER_DELAY_MS] }
    val countdownMs: Flow<Long?> = context.dataStore.data.map { it[Keys.COUNTDOWN_MS] }
    val diagnosticsEnabled: Flow<Boolean> = context.dataStore.data.map { it[Keys.DIAGNOSTICS_ENABLED] ?: false }
```

After `savePrebuffer(...)`:

```kotlin
    suspend fun saveKeybinds(serialized: String) {
        context.dataStore.edit { it[Keys.KEYBINDS] = serialized }
    }

    suspend fun savePrebufferDelayMs(ms: Long) {
        context.dataStore.edit { it[Keys.PREBUFFER_DELAY_MS] = ms }
    }

    suspend fun saveCountdownMs(ms: Long) {
        context.dataStore.edit { it[Keys.COUNTDOWN_MS] = ms }
    }

    suspend fun saveDiagnosticsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DIAGNOSTICS_ENABLED] = enabled }
    }

    /** Settings → Advanced → Restore defaults: that page's settings plus every added button. */
    suspend fun clearAdvancedSettings() {
        context.dataStore.edit {
            it.remove(Keys.KEYBINDS)
            it.remove(Keys.PREBUFFER_DELAY_MS)
            it.remove(Keys.COUNTDOWN_MS)
            it.remove(Keys.DIAGNOSTICS_ENABLED)
        }
    }
```

- [ ] **Step 2: Add the runtime switch to the sentry `CrashReporting`.** Replace the lines from `object CrashReporting {` down to (and including) the end of `fun log(...)` with:

```kotlin
object CrashReporting {

    /** This flavor can send diagnostic logs, so Settings offers the switch. */
    const val SUPPORTS_DIAGNOSTICS = true

    // Set from Settings → Advanced once prefs are read; early-startup lines only reach Sentry
    // in debug/beta builds.
    @Volatile private var diagnosticsOptIn = false

    private val verbose get() = BuildConfig.DEBUG || BuildConfig.PRERELEASE || diagnosticsOptIn

    fun init(application: Application) {
        SentryAndroid.init(application) { options ->
            options.dsn = BuildConfig.SENTRY_DSN
            options.environment = when {
                BuildConfig.DEBUG -> "debug"
                BuildConfig.PRERELEASE -> "beta"
                else -> "release"
            }
            options.release = "tellyfin@${BuildConfig.VERSION_NAME}"
            // Log streaming is gated per call via [verbose] (debug, beta, or the user's opt-in),
            // so it can be switched on at runtime without re-initialising Sentry.
            options.logs.isEnabled = true
        }
    }

    fun setDiagnosticsEnabled(enabled: Boolean) {
        diagnosticsOptIn = enabled
    }

    fun addBreadcrumb(message: String, category: String) {
        Sentry.addBreadcrumb(message, category)
        if (verbose) Sentry.logger().info(message)
    }

    /** Streams a diagnostic log line to Sentry — debug/beta builds or opted-in users only. */
    fun log(message: String) {
        if (verbose) Sentry.logger().info(message)
    }
```

Keep `captureException`, `captureMessage` (it already checks `if (!verbose) return`) and the level mappers unchanged.

- [ ] **Step 3: Mirror it in the noSentry `CrashReporting`** — add inside the object:

```kotlin
    /** Nothing here can send anything, so Settings hides the diagnostic-logs switch. */
    const val SUPPORTS_DIAGNOSTICS = false

    fun setDiagnosticsEnabled(enabled: Boolean) {}
```

- [ ] **Step 4: Verify both flavors compile**

Run: `./gradlew assembleSentryDirectDebug assembleNoSentryDirectDebug -q`
Expected: build succeeds.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/tellyfin/androidtv/data/prefs/PreferencesRepository.kt app/src/sentry/java/app/tellyfin/androidtv/diagnostics/CrashReporting.kt app/src/noSentry/java/app/tellyfin/androidtv/diagnostics/CrashReporting.kt
git commit -m "Store the new settings and let users opt in to diagnostic logs"
```

---

### Task 5: Paged settings screen wired into the player

This task swaps the old screen for the new one; the ViewModel and UI changes only compile together, so they land in one commit.

**Files:**
- Create: `app/src/main/res/drawable/ic_settings_streaming.xml`, `ic_settings_remote.xml`, `ic_settings_app.xml`, `ic_settings_account.xml`, `ic_settings_advanced.xml`
- Create: `app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsPages.kt`
- Rewrite: `app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsScreen.kt`
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerViewModel.kt`
- Modify: `app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`, `app/src/main/res/values-de/strings.xml`

**Interfaces:**
- Consumes: everything produced by Tasks 1–4.
- Produces: `PlayerViewModel.settingsContext: SettingsContext` (public); `PlayerUiState.settings`, `.keybinds`, `.prebufferDelayMs`, `.countdownSettingMs`, `.diagnosticsEnabled`; `SettingsScreen(state: PlayerUiState, context: SettingsContext, serverUrl: String, appVersion: String, modifier: Modifier)`.

- [ ] **Step 1: Add the five icons.** Material Symbols Rounded (Apache 2.0), converted from `viewBox="0 -960 960 960"`. Each file uses this template — only the `pathData` differs:

`app/src/main/res/drawable/ic_settings_streaming.xml` (Material Symbol `wifi`):
```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Material Symbols Rounded "wifi" (Apache 2.0) -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="960"
    android:viewportHeight="960">
    <group android:translateY="960">
        <path
            android:fillColor="#FFFFFFFF"
            android:pathData="M480-120q-42 0-71-29t-29-71q0-42 29-71t71-29q42 0 71 29t29 71q0 42-29 71t-71 29Zm0-440q75 0 142.5 24T745-470q20 15 20.5 39.5T748-388q-17 17-42 17.5T661-384q-38-26-84-41t-97-15q-51 0-97 15t-84 41q-20 14-45 13t-42-18q-17-18-17-42.5t20-39.5q55-42 122.5-65.5T480-560Zm0-240q125 0 235.5 41T914-643q20 17 21 42t-17 43q-17 17-42 17.5T831-556q-72-59-161.5-91.5T480-680q-100 0-189.5 32.5T129-556q-20 16-45 15.5T42-558q-18-18-17-43t21-42q88-75 198.5-116T480-800Z" />
    </group>
</vector>
```

`ic_settings_remote.xml` (Material Symbol `settings_remote`), same template with comment `"settings_remote"` and:
```
M360-40q-17 0-28.5-11.5T320-80v-480q0-17 11.5-28.5T360-600h240q17 0 28.5 11.5T640-560v480q0 17-11.5 28.5T600-40H360Zm120-350q21 0 35.5-14.5T530-440q0-21-14.5-35.5T480-490q-21 0-35.5 14.5T430-440q0 21 14.5 35.5T480-390Zm0-330q-30 0-58 8t-53 25q-14 9-31 8.5T310-690q-12-12-11.5-28t13.5-26q36-27 79-41.5t89-14.5q46 0 89 14.5t79 41.5q13 10 13.5 26T650-690q-11 11-27 12t-30-8q-25-17-54-25.5t-59-8.5Zm0-160q-62 0-119 19.5T257-803q-14 11-31 11.5T197-803q-12-12-12-29t14-28q60-48 131.5-74T480-960q78 0 150 25.5T760-859q13 11 13.5 28T762-802q-11 11-27.5 11.5T705-801q-48-39-105-59t-120-20Zm-80 760h160v-400H400v400Zm0 0h160-160Z
```

`ic_settings_app.xml` (Material Symbol `info`), same template with comment `"info"` and:
```
M480-280q17 0 28.5-11.5T520-320v-160q0-17-11.5-28.5T480-520q-17 0-28.5 11.5T440-480v160q0 17 11.5 28.5T480-280Zm0-320q17 0 28.5-11.5T520-640q0-17-11.5-28.5T480-680q-17 0-28.5 11.5T440-640q0 17 11.5 28.5T480-600Zm0 520q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q134 0 227-93t93-227q0-134-93-227t-227-93q-134 0-227 93t-93 227q0 134 93 227t227 93Zm0-320Z
```

`ic_settings_account.xml` (Material Symbol `account_circle`), same template with comment `"account_circle"` and:
```
M234-276q51-39 114-61.5T480-360q69 0 132 22.5T726-276q35-41 54.5-93T800-480q0-133-93.5-226.5T480-800q-133 0-226.5 93.5T160-480q0 59 19.5 111t54.5 93Zm246-164q-59 0-99.5-40.5T340-580q0-59 40.5-99.5T480-720q59 0 99.5 40.5T620-580q0 59-40.5 99.5T480-440Zm0 360q-83 0-156-31.5T197-197q-54-54-85.5-127T80-480q0-83 31.5-156T197-763q54-54 127-85.5T480-880q83 0 156 31.5T763-763q54 54 85.5 127T880-480q0 83-31.5 156T763-197q-54 54-127 85.5T480-80Zm0-80q53 0 100-15.5t86-44.5q-39-29-86-44.5T480-280q-53 0-100 15.5T294-220q39 29 86 44.5T480-160Zm0-360q26 0 43-17t17-43q0-26-17-43t-43-17q-26 0-43 17t-17 43q0 26 17 43t43 17Zm0-60Zm0 360Z
```

`ic_settings_advanced.xml` (Material Symbol `tune`), same template with comment `"tune"` and:
```
M480-120q-17 0-28.5-11.5T440-160v-160q0-17 11.5-28.5T480-360q17 0 28.5 11.5T520-320v40h280q17 0 28.5 11.5T840-240q0 17-11.5 28.5T800-200H520v40q0 17-11.5 28.5T480-120Zm-320-80q-17 0-28.5-11.5T120-240q0-17 11.5-28.5T160-280h160q17 0 28.5 11.5T360-240q0 17-11.5 28.5T320-200H160Zm160-160q-17 0-28.5-11.5T280-400v-40H160q-17 0-28.5-11.5T120-480q0-17 11.5-28.5T160-520h120v-40q0-17 11.5-28.5T320-600q17 0 28.5 11.5T360-560v160q0 17-11.5 28.5T320-360Zm160-80q-17 0-28.5-11.5T440-480q0-17 11.5-28.5T480-520h320q17 0 28.5 11.5T840-480q0 17-11.5 28.5T800-440H480Zm160-160q-17 0-28.5-11.5T600-640v-160q0-17 11.5-28.5T640-840q17 0 28.5 11.5T680-800v40h120q17 0 28.5 11.5T840-720q0 17-11.5 28.5T800-680H680v40q0 17-11.5 28.5T640-600Zm-480-80q-17 0-28.5-11.5T120-720q0-17 11.5-28.5T160-760h320q17 0 28.5 11.5T520-720q0 17-11.5 28.5T480-680H160Z
```

- [ ] **Step 2: Strings.** In `values/strings.xml`, delete the line `<string name="settings_hint">…</string>` and add after `settings_bandwidth_hint`:

```xml
    <string name="settings_section_keybinds">Remote &amp; Keybinds</string>
    <string name="settings_section_advanced">Advanced</string>
    <string name="settings_summary_streaming">Bandwidth, pre-buffering</string>
    <string name="settings_summary_keybinds">Extra buttons</string>
    <string name="settings_summary_app">Version, updates</string>
    <string name="settings_summary_account">Server, sign out</string>
    <string name="settings_summary_advanced">Timing, diagnostics</string>
    <string name="settings_hint_rail">↑↓ Section  ·  → / OK Open  ·  BACK Close</string>
    <string name="settings_hint_pane">↑↓ Navigate  ·  OK Select  ·  ← Back</string>
    <string name="keybinds_intro">Add an extra button to any action — the standard button keeps working.</string>
    <string name="keybind_quick_menu">Quick menu</string>
    <string name="keybind_channel_info">Channel info</string>
    <string name="keybind_live_guide">Live guide</string>
    <string name="keybind_search">Search</string>
    <string name="keybind_channel_up">Channel up</string>
    <string name="keybind_channel_down">Channel down</string>
    <string name="keybind_hold_ok">hold OK</string>
    <string name="keybind_capture_title">Press the button to use for</string>
    <string name="keybind_capture_reserved">That button can\'t be used.</string>
    <string name="keybind_capture_conflict">%1$s already opens %2$s.</string>
    <string name="keybind_capture_not_passed">Nothing happening? That button isn\'t passed to apps.</string>
    <string name="keybind_capture_keys">BACK Cancel  ·  OK Remove extra button</string>
    <string name="settings_group_switching">CHANNEL SWITCHING</string>
    <string name="settings_group_diagnostics">DIAGNOSTICS</string>
    <string name="settings_prebuffer_delay">Pre-buffer delay</string>
    <string name="settings_prebuffer_delay_hint">How long a channel must stay highlighted before it starts loading.</string>
    <string name="settings_countdown">Countdown</string>
    <string name="settings_countdown_hint">How long the switch banner counts down. Auto: 5 s with pre-buffering, 3 s without.</string>
    <string name="settings_countdown_auto">Auto</string>
    <string name="settings_countdown_auto_value">Auto (%s)</string>
    <string name="settings_diagnostics">Send diagnostic logs</string>
    <string name="settings_diagnostics_hint">Streams detailed logs to the developer\'s Sentry to help track down problems.</string>
    <string name="settings_restore_defaults">Restore defaults</string>
    <string name="settings_restore_defaults_hint">Resets the settings on this page and removes all added buttons.</string>
```

In `values-de/strings.xml`, delete `settings_hint` and add after `settings_bandwidth_hint`:

```xml
    <string name="settings_section_keybinds">Fernbedienung &amp; Tasten</string>
    <string name="settings_section_advanced">Erweitert</string>
    <string name="settings_summary_streaming">Bandbreite, Vorpuffern</string>
    <string name="settings_summary_keybinds">Zusätzliche Tasten</string>
    <string name="settings_summary_app">Version, Updates</string>
    <string name="settings_summary_account">Server, Abmelden</string>
    <string name="settings_summary_advanced">Timing, Diagnose</string>
    <string name="settings_hint_rail">↑↓ Bereich  ·  → / OK Öffnen  ·  ZURÜCK Schließen</string>
    <string name="settings_hint_pane">↑↓ Navigation  ·  OK Auswählen  ·  ← Zurück</string>
    <string name="keybinds_intro">Füge jeder Aktion eine zusätzliche Taste hinzu — die Standardtaste funktioniert weiterhin.</string>
    <string name="keybind_quick_menu">Schnellmenü</string>
    <string name="keybind_channel_info">Kanalinfo</string>
    <string name="keybind_live_guide">TV-Programm</string>
    <string name="keybind_search">Suche</string>
    <string name="keybind_channel_up">Kanal hoch</string>
    <string name="keybind_channel_down">Kanal runter</string>
    <string name="keybind_hold_ok">OK halten</string>
    <string name="keybind_capture_title">Drücke die Taste für</string>
    <string name="keybind_capture_reserved">Diese Taste kann nicht verwendet werden.</string>
    <string name="keybind_capture_conflict">%1$s öffnet bereits %2$s.</string>
    <string name="keybind_capture_not_passed">Passiert nichts? Diese Taste wird nicht an Apps weitergegeben.</string>
    <string name="keybind_capture_keys">ZURÜCK Abbrechen  ·  OK Zusatztaste entfernen</string>
    <string name="settings_group_switching">KANALWECHSEL</string>
    <string name="settings_group_diagnostics">DIAGNOSE</string>
    <string name="settings_prebuffer_delay">Vorpuffer-Verzögerung</string>
    <string name="settings_prebuffer_delay_hint">Wie lange ein Kanal markiert sein muss, bevor er zu laden beginnt.</string>
    <string name="settings_countdown">Countdown</string>
    <string name="settings_countdown_hint">Wie lange das Wechsel-Banner herunterzählt. Auto: 5 s mit Vorpuffern, 3 s ohne.</string>
    <string name="settings_countdown_auto">Auto</string>
    <string name="settings_countdown_auto_value">Auto (%s)</string>
    <string name="settings_diagnostics">Diagnoseprotokolle senden</string>
    <string name="settings_diagnostics_hint">Sendet ausführliche Protokolle an das Sentry des Entwicklers, um Probleme aufzuspüren.</string>
    <string name="settings_restore_defaults">Standard wiederherstellen</string>
    <string name="settings_restore_defaults_hint">Setzt die Einstellungen dieser Seite zurück und entfernt alle hinzugefügten Tasten.</string>
```

- [ ] **Step 3: Create `SettingsPages.kt`** (page contents and shared building blocks):

```kotlin
package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors
import java.util.Locale

// Contents of each settings page. Focusable rows must appear in exactly the order
// settingsRows() lists them — SettingsNavigator's row index points into that list.

@Composable
fun SettingsPageContent(
    page: SettingsPage,
    state: PlayerUiState,
    context: SettingsContext,
    focusedRow: SettingsRow?,
    serverUrl: String,
    appVersion: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        when (page) {
            SettingsPage.STREAMING -> StreamingPage(state, focusedRow)
            SettingsPage.KEYBINDS -> KeybindsPage(state.keybinds, focusedRow)
            SettingsPage.APP -> AppPage(state.updateStatus, appVersion, context, focusedRow)
            SettingsPage.ACCOUNT -> AccountPage(serverUrl, state.username, focusedRow)
            SettingsPage.ADVANCED -> AdvancedPage(state, context, focusedRow)
        }
    }
}

@Composable
private fun StreamingPage(state: PlayerUiState, focusedRow: SettingsRow?) {
    val bandwidthFocused = focusedRow == SettingsRow.Bandwidth
    SettingsRowItem(stringResource(R.string.settings_bandwidth), bandwidthFocused) {
        ValueText(bitrateLabel(state.maxBitrate), bandwidthFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_bandwidth_hint))

    val prebufferFocused = focusedRow == SettingsRow.PrebufferToggle
    SettingsRowItem(stringResource(R.string.settings_prebuffer), prebufferFocused) {
        ValueText(onOff(state.prebufferEnabled), prebufferFocused)
    }
    Hint(
        stringResource(
            if (state.prebufferAutoDisabled) R.string.settings_prebuffer_auto_off_hint
            else R.string.settings_prebuffer_hint
        ),
        isWarning = state.prebufferAutoDisabled
    )
}

@Composable
private fun KeybindsPage(keybinds: Keybinds, focusedRow: SettingsRow?) {
    Hint(stringResource(R.string.keybinds_intro))
    Spacer(Modifier.height(4.dp))
    KeyAction.entries.forEach { action ->
        SettingsRowItem(stringResource(action.labelRes), focusedRow == SettingsRow.Keybind(action)) {
            KeyChips(action, keybinds)
        }
    }
}

@Composable
private fun AppPage(updateStatus: UpdateStatus, appVersion: String, context: SettingsContext, focusedRow: SettingsRow?) {
    InfoLine(stringResource(R.string.settings_version), if (appVersion.isNotBlank()) "v$appVersion" else "—")
    if (context.selfUpdateEnabled) {
        Spacer(Modifier.height(6.dp))
        SettingsRowItem(stringResource(R.string.settings_update_label), focusedRow == SettingsRow.Update) {
            UpdateStatusValue(updateStatus)
        }
    }
}

@Composable
private fun AccountPage(serverUrl: String, username: String, focusedRow: SettingsRow?) {
    InfoLine(stringResource(R.string.settings_server), serverUrl.ifBlank { "—" })
    InfoLine(stringResource(R.string.settings_username_label), username.ifBlank { "—" })
    Spacer(Modifier.height(10.dp))
    SettingsRowItem(stringResource(R.string.settings_logout), focusedRow == SettingsRow.SignOut, danger = true)
}

@Composable
private fun AdvancedPage(state: PlayerUiState, context: SettingsContext, focusedRow: SettingsRow?) {
    GroupLabel(stringResource(R.string.settings_group_switching))
    val delayFocused = focusedRow == SettingsRow.PrebufferDelay
    SettingsRowItem(stringResource(R.string.settings_prebuffer_delay), delayFocused) {
        ValueText(formatSeconds(state.prebufferDelayMs), delayFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_prebuffer_delay_hint))
    val countdownFocused = focusedRow == SettingsRow.Countdown
    SettingsRowItem(stringResource(R.string.settings_countdown), countdownFocused) {
        ValueText(countdownLabel(state.countdownSettingMs, state.prebufferEnabled), countdownFocused, chevron = true)
    }
    Hint(stringResource(R.string.settings_countdown_hint))

    if (context.diagnosticsAvailable) {
        GroupLabel(stringResource(R.string.settings_group_diagnostics))
        val diagnosticsFocused = focusedRow == SettingsRow.Diagnostics
        SettingsRowItem(stringResource(R.string.settings_diagnostics), diagnosticsFocused) {
            ValueText(onOff(state.diagnosticsEnabled), diagnosticsFocused)
        }
        Hint(stringResource(R.string.settings_diagnostics_hint))
    }

    Spacer(Modifier.height(8.dp))
    SettingsRowItem(stringResource(R.string.settings_restore_defaults), focusedRow == SettingsRow.RestoreDefaults, danger = true)
    Hint(stringResource(R.string.settings_restore_defaults_hint))
}

// ── Labels ────────────────────────────────────────────────────────────────────

val KeyAction.labelRes: Int
    get() = when (this) {
        KeyAction.QUICK_MENU -> R.string.keybind_quick_menu
        KeyAction.CHANNEL_INFO -> R.string.keybind_channel_info
        KeyAction.LIVE_GUIDE -> R.string.keybind_live_guide
        KeyAction.SEARCH -> R.string.keybind_search
        KeyAction.CHANNEL_UP -> R.string.keybind_channel_up
        KeyAction.CHANNEL_DOWN -> R.string.keybind_channel_down
    }

fun formatSeconds(ms: Long): String =
    if (ms % 1000 == 0L) "${ms / 1000} s" else String.format(Locale.getDefault(), "%.1f s", ms / 1000f)

@Composable
fun countdownLabel(settingMs: Long, prebufferEnabled: Boolean): String =
    if (settingMs == Prebuffer.COUNTDOWN_AUTO)
        stringResource(R.string.settings_countdown_auto_value, formatSeconds(Prebuffer.countdownMs(prebufferEnabled)))
    else formatSeconds(settingMs)

private fun bitrateLabel(bitrate: Int?): String =
    BITRATE_OPTIONS.firstOrNull { it.first == bitrate }?.second ?: BITRATE_OPTIONS.first().second

@Composable
private fun onOff(enabled: Boolean): String =
    stringResource(if (enabled) R.string.settings_on else R.string.settings_off)

// ── Building blocks ───────────────────────────────────────────────────────────

/** Focusable row — bordered pill; purple when focused, red for destructive actions. */
@Composable
fun SettingsRowItem(
    label: String,
    focused: Boolean,
    danger: Boolean = false,
    value: @Composable () -> Unit = {}
) {
    val accent = if (danger) AppColors.Red else AppColors.Purple
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) Modifier.background(accent.copy(alpha = 0.20f), shape).border(1.dp, accent, shape)
                else Modifier
                    .background(Color.White.copy(alpha = 0.04f), shape)
                    .border(1.dp, Color.White.copy(alpha = 0.10f), shape)
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = when {
                focused && danger -> AppColors.Red
                focused -> Color.White
                else -> AppColors.OnSurface.copy(alpha = 0.75f)
            },
            fontSize = 14.sp,
            fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal
        )
        value()
    }
}

@Composable
private fun ValueText(text: String, focused: Boolean, chevron: Boolean = false) {
    Text(
        if (chevron) "$text  ›" else text,
        color = if (focused) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.60f),
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun Hint(text: String, isWarning: Boolean = false) {
    Text(
        text,
        color = if (isWarning) AppColors.Red.copy(alpha = 0.75f) else AppColors.OnSurface.copy(alpha = 0.35f),
        fontSize = 11.sp,
        lineHeight = 15.sp,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        color = AppColors.Purple.copy(alpha = 0.80f),
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

/** Read-only label/value pair — visually flat so it can't be mistaken for a button. */
@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.OnSurface.copy(alpha = 0.45f), fontSize = 13.sp)
        Spacer(Modifier.width(16.dp))
        Text(
            value,
            color = AppColors.OnSurface.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** The buttons that trigger an action: grey = built in, purple = added by the user. */
@Composable
fun KeyChips(action: KeyAction, keybinds: Keybinds) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        KeyChip(keyLabel(action.defaultKeyCode))
        when (action) {
            KeyAction.QUICK_MENU -> KeyChip(stringResource(R.string.keybind_hold_ok))
            KeyAction.CHANNEL_UP -> KeyChip("▲")
            KeyAction.CHANNEL_DOWN -> KeyChip("▼")
            else -> Unit
        }
        keybinds.extras[action]?.let { KeyChip(keyLabel(it), added = true) }
    }
}

@Composable
private fun KeyChip(label: String, added: Boolean = false) {
    Text(
        label,
        color = if (added) Color(0xFFC9B3FF) else AppColors.OnSurface.copy(alpha = 0.70f),
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .background(
                if (added) AppColors.Purple.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun UpdateStatusValue(updateStatus: UpdateStatus) {
    when (updateStatus) {
        UpdateStatus.Idle ->
            Text("—", color = AppColors.OnSurface.copy(alpha = 0.55f), fontSize = 13.sp)
        UpdateStatus.UpToDate ->
            Text(
                stringResource(R.string.settings_update_up_to_date),
                color = AppColors.OnSurface.copy(alpha = 0.55f), fontSize = 13.sp
            )
        UpdateStatus.Checking ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(
                    color = AppColors.Purple.copy(alpha = 0.60f),
                    trackColor = Color.Transparent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    stringResource(R.string.settings_update_checking),
                    color = AppColors.OnSurface.copy(alpha = 0.45f), fontSize = 12.sp
                )
            }
        is UpdateStatus.Available ->
            Text(
                stringResource(R.string.settings_update_available, updateStatus.version),
                color = AppColors.Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        is UpdateStatus.Downloading ->
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    stringResource(R.string.settings_update_downloading, updateStatus.progress),
                    color = AppColors.Purple.copy(alpha = 0.80f), fontSize = 12.sp
                )
                Spacer(Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { updateStatus.progress / 100f },
                    modifier = Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AppColors.Purple,
                    trackColor = Color.White.copy(alpha = 0.12f)
                )
            }
        UpdateStatus.ReadyToInstall ->
            Text(
                stringResource(R.string.settings_update_ready),
                color = AppColors.Purple, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
            )
        is UpdateStatus.Error ->
            Text(
                stringResource(R.string.settings_update_error),
                color = AppColors.Red.copy(alpha = 0.80f), fontSize = 12.sp
            )
    }
}
```

- [ ] **Step 4: Replace the whole of `SettingsScreen.kt`** with the rail, pane frame and dialogs:

```kotlin
package app.tellyfin.androidtv.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tellyfin.androidtv.R
import app.tellyfin.androidtv.ui.theme.AppColors

// Paged settings, modelled on Android TV's own: a rail of pages on the left, the selected
// page's rows on the right. Key handling lives in SettingsNavigator; this only draws state.

@Composable
fun SettingsScreen(
    state: PlayerUiState,
    context: SettingsContext,
    serverUrl: String,
    appVersion: String,
    modifier: Modifier = Modifier
) {
    val settings = state.settings
    val focusedRow = if (settings.inPane) settingsRows(settings.page, context).getOrNull(settings.row) else null

    Box(modifier = modifier.fillMaxSize().background(AppColors.Background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            SettingsRail(
                selected = settings.page,
                railFocused = !settings.inPane,
                modifier = Modifier.width(320.dp).fillMaxHeight()
            )
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.06f)))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 48.dp, vertical = 40.dp)
            ) {
                Text(
                    stringResource(settings.page.titleRes),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(24.dp))
                Box(modifier = Modifier.weight(1f)) {
                    SettingsPageContent(settings.page, state, context, focusedRow, serverUrl, appVersion)
                }
                Text(
                    stringResource(if (settings.inPane) R.string.settings_hint_pane else R.string.settings_hint_rail),
                    color = AppColors.OnSurface.copy(alpha = 0.30f),
                    fontSize = 11.sp
                )
            }
        }

        settings.picker?.let { PickerDialog(it, state.prebufferEnabled) }
        settings.capture?.let { CaptureDialog(it, state.keybinds) }
    }
}

private val SettingsPage.titleRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.string.settings_section_streaming
        SettingsPage.KEYBINDS -> R.string.settings_section_keybinds
        SettingsPage.APP -> R.string.settings_section_app
        SettingsPage.ACCOUNT -> R.string.settings_section_account
        SettingsPage.ADVANCED -> R.string.settings_section_advanced
    }

private val SettingsPage.summaryRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.string.settings_summary_streaming
        SettingsPage.KEYBINDS -> R.string.settings_summary_keybinds
        SettingsPage.APP -> R.string.settings_summary_app
        SettingsPage.ACCOUNT -> R.string.settings_summary_account
        SettingsPage.ADVANCED -> R.string.settings_summary_advanced
    }

private val SettingsPage.iconRes: Int
    get() = when (this) {
        SettingsPage.STREAMING -> R.drawable.ic_settings_streaming
        SettingsPage.KEYBINDS -> R.drawable.ic_settings_remote
        SettingsPage.APP -> R.drawable.ic_settings_app
        SettingsPage.ACCOUNT -> R.drawable.ic_settings_account
        SettingsPage.ADVANCED -> R.drawable.ic_settings_advanced
    }

@Composable
private fun SettingsRail(selected: SettingsPage, railFocused: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 40.dp)) {
        Text(
            stringResource(R.string.settings),
            color = Color.White,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 12.dp, bottom = 28.dp)
        )
        SettingsPage.entries.forEach { page ->
            RailItem(page, isSelected = page == selected, isFocused = railFocused && page == selected)
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** Focused (rail has focus) = purple highlight; selected while the pane has focus = subtle. */
@Composable
private fun RailItem(page: SettingsPage, isSelected: Boolean, isFocused: Boolean) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                when {
                    isFocused -> Modifier
                        .background(AppColors.Purple.copy(alpha = 0.14f), shape)
                        .border(1.dp, AppColors.Purple.copy(alpha = 0.50f), shape)
                    isSelected -> Modifier.background(Color.White.copy(alpha = 0.05f), shape)
                    else -> Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    if (isFocused) AppColors.Purple.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f),
                    RoundedCornerShape(8.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(page.iconRes),
                contentDescription = null,
                tint = if (isSelected) Color.White else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                stringResource(page.titleRes),
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                fontSize = 14.sp,
                fontWeight = if (isFocused) FontWeight.SemiBold else FontWeight.Normal
            )
            Text(
                stringResource(page.summaryRes),
                color = Color.White.copy(alpha = 0.38f),
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun DialogFrame(content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(380.dp)
                .background(AppColors.Surface, RoundedCornerShape(12.dp))
                .border(1.dp, AppColors.OnSurface.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(20.dp),
            content = content
        )
    }
}

@Composable
private fun PickerDialog(picker: PickerState, prebufferEnabled: Boolean) {
    val title = stringResource(
        when (picker.kind) {
            PickerKind.BANDWIDTH -> R.string.settings_bandwidth
            PickerKind.PREBUFFER_DELAY -> R.string.settings_prebuffer_delay
            PickerKind.COUNTDOWN -> R.string.settings_countdown
        }
    )
    val options = when (picker.kind) {
        PickerKind.BANDWIDTH -> BITRATE_OPTIONS.map { it.second }
        PickerKind.PREBUFFER_DELAY -> Prebuffer.START_DELAY_OPTIONS.map(::formatSeconds)
        PickerKind.COUNTDOWN -> Prebuffer.COUNTDOWN_OPTIONS.map { countdownLabel(it, prebufferEnabled) }
    }
    DialogFrame {
        Text(
            title,
            color = Color.White,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 14.dp)
        )
        options.forEachIndexed { idx, label ->
            PickerOption(label, isHighlighted = idx == picker.index)
            if (idx < options.lastIndex) Spacer(Modifier.height(3.dp))
        }
    }
}

@Composable
private fun PickerOption(label: String, isHighlighted: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isHighlighted) Modifier
                    .background(AppColors.Purple.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                    .border(1.dp, AppColors.Purple, RoundedCornerShape(6.dp))
                else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            label,
            color = if (isHighlighted) AppColors.Purple else AppColors.OnSurface.copy(alpha = 0.75f),
            fontSize = 14.sp,
            fontWeight = if (isHighlighted) FontWeight.SemiBold else FontWeight.Normal
        )
        if (isHighlighted) Text("●", color = AppColors.Purple, fontSize = 8.sp)
    }
}

@Composable
private fun CaptureDialog(capture: CaptureState, keybinds: Keybinds) {
    DialogFrame {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                painter = painterResource(R.drawable.ic_settings_remote),
                contentDescription = null,
                tint = AppColors.Purple,
                modifier = Modifier.size(32.dp)
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.keybind_capture_title),
                color = Color.White,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Text(
                stringResource(capture.action.labelRes),
                color = AppColors.Purple,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            KeyChips(capture.action, keybinds)
            capture.message?.let { message ->
                Spacer(Modifier.height(12.dp))
                Text(
                    when (message) {
                        CaptureMessage.Reserved -> stringResource(R.string.keybind_capture_reserved)
                        is CaptureMessage.Conflict -> stringResource(
                            R.string.keybind_capture_conflict,
                            keyLabel(message.keyCode),
                            stringResource(message.owner.labelRes)
                        )
                    },
                    color = AppColors.Red,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.keybind_capture_not_passed),
                color = AppColors.OnSurface.copy(alpha = 0.45f),
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.keybind_capture_keys),
                color = AppColors.OnSurface.copy(alpha = 0.60f),
                fontSize = 11.sp
            )
        }
    }
}
```

- [ ] **Step 5: Update `PlayerViewModel.kt`.**

5a. Delete the four `SETTINGS_FOCUS_*` constants and their comment line (`// Settings screen focus targets …`).

5b. In `data class PlayerUiState`, delete `val bitratePickerOpen: Boolean = false,` and `val bitratePickerIndex: Int = 0,`; after `val prebufferAutoDisabled: Boolean = false,` add:

```kotlin
    val prebufferDelayMs: Long = Prebuffer.DEFAULT_START_DELAY_MS,
    /** Prebuffer.COUNTDOWN_AUTO or an explicit countdown in ms. */
    val countdownSettingMs: Long = Prebuffer.COUNTDOWN_AUTO,
    val diagnosticsEnabled: Boolean = false,
    val keybinds: Keybinds = Keybinds(),
    val settings: SettingsState = SettingsState(),
```

5c. Next to `private val updateChecker = UpdateChecker(application)` add:

```kotlin
    val settingsContext = SettingsContext(
        selfUpdateEnabled = BuildConfig.SELF_UPDATE_ENABLED,
        diagnosticsAvailable = CrashReporting.SUPPORTS_DIAGNOSTICS
    )
```

5d. In the `init` coroutine, after `val prebufferAutoDisabled = prefsRepo.prebufferAutoDisabled.first()` add:

```kotlin
                val keybinds = Keybinds.parse(prefsRepo.keybinds.first())
                val prebufferDelayMs = prefsRepo.prebufferDelayMs.first() ?: Prebuffer.DEFAULT_START_DELAY_MS
                val countdownSettingMs = prefsRepo.countdownMs.first() ?: Prebuffer.COUNTDOWN_AUTO
                val diagnosticsEnabled = prefsRepo.diagnosticsEnabled.first()
                CrashReporting.setDiagnosticsEnabled(diagnosticsEnabled)
```

and in the `_uiState.value = _uiState.value.copy(` right below, after `prebufferAutoDisabled = prebufferAutoDisabled,` add:

```kotlin
                    keybinds = keybinds,
                    prebufferDelayMs = prebufferDelayMs,
                    countdownSettingMs = countdownSettingMs,
                    diagnosticsEnabled = diagnosticsEnabled,
```

5e. Replace the start of `handleKeyEvent` —

```kotlin
    fun handleKeyEvent(keyCode: Int): Boolean {
        val state = _uiState.value

        if (keyCode == KeyEvent.KEYCODE_BACK) return handleBack(state)
```

— with:

```kotlin
    fun handleKeyEvent(rawKeyCode: Int): Boolean {
        val state = _uiState.value

        // The capture dialog needs the button exactly as pressed (including Back and Search);
        // everywhere else a user-added button stands in for its action's standard one.
        if (state.overlay is Overlay.Settings && state.settings.capture != null) {
            return handleSettingsKeys(rawKeyCode, state)
        }
        val keyCode = state.keybinds.resolve(rawKeyCode)

        if (keyCode == KeyEvent.KEYCODE_BACK) return handleBack(state)
```

5f. In `handleBack`, replace the first branch

```kotlin
            state.bitratePickerOpen -> {
                _uiState.value = state.copy(bitratePickerOpen = false)
                true
            }
```

with

```kotlin
            state.overlay is Overlay.Settings -> handleSettingsKeys(KeyEvent.KEYCODE_BACK, state)
```

5g. Replace the whole `private fun handleSettingsKeys(...)` function with:

```kotlin
    private fun handleSettingsKeys(keyCode: Int, state: PlayerUiState): Boolean {
        val result = SettingsNavigator.onKey(
            state.settings,
            keyCode,
            settingsContext,
            SettingsValues(state.maxBitrate, state.prebufferDelayMs, state.countdownSettingMs, state.keybinds)
        )
        _uiState.value = _uiState.value.copy(settings = result.state)
        result.command?.let(::runSettingsCommand)
        return result.handled
    }

    private fun runSettingsCommand(command: SettingsCommand) {
        val state = _uiState.value
        when (command) {
            SettingsCommand.Close ->
                _uiState.value = state.copy(overlay = Overlay.None, settings = SettingsState())
            SettingsCommand.TogglePrebuffer -> setPrebufferEnabled(!state.prebufferEnabled)
            SettingsCommand.ToggleDiagnostics -> setDiagnosticsEnabled(!state.diagnosticsEnabled)
            SettingsCommand.ActivateUpdate -> when (val status = state.updateStatus) {
                is UpdateStatus.Available -> downloadUpdate(status.version)
                UpdateStatus.ReadyToInstall -> triggerInstall()
                else -> Unit
            }
            SettingsCommand.SignOut -> logOut()
            SettingsCommand.RestoreDefaults -> restoreAdvancedDefaults()
            is SettingsCommand.PickBitrate -> setMaxBitrate(command.bitrate)
            is SettingsCommand.PickPrebufferDelay -> {
                _uiState.value = state.copy(prebufferDelayMs = command.ms)
                viewModelScope.launch { prefsRepo.savePrebufferDelayMs(command.ms) }
            }
            is SettingsCommand.PickCountdown -> {
                _uiState.value = state.copy(countdownSettingMs = command.ms)
                viewModelScope.launch { prefsRepo.saveCountdownMs(command.ms) }
            }
            is SettingsCommand.SetExtraKey -> {
                val keybinds = state.keybinds.withExtra(command.action, command.keyCode)
                _uiState.value = state.copy(keybinds = keybinds)
                viewModelScope.launch { prefsRepo.saveKeybinds(keybinds.serialize()) }
            }
        }
    }

    private fun setDiagnosticsEnabled(enabled: Boolean) {
        CrashReporting.setDiagnosticsEnabled(enabled)
        _uiState.value = _uiState.value.copy(diagnosticsEnabled = enabled)
        viewModelScope.launch { prefsRepo.saveDiagnosticsEnabled(enabled) }
    }

    /** Settings → Advanced → Restore defaults; bandwidth, pre-buffer on/off and account stay. */
    private fun restoreAdvancedDefaults() {
        CrashReporting.setDiagnosticsEnabled(false)
        _uiState.value = _uiState.value.copy(
            prebufferDelayMs = Prebuffer.DEFAULT_START_DELAY_MS,
            countdownSettingMs = Prebuffer.COUNTDOWN_AUTO,
            diagnosticsEnabled = false,
            keybinds = Keybinds()
        )
        viewModelScope.launch { prefsRepo.clearAdvancedSettings() }
    }
```

5h. Replace `openSettings()`'s body line with:

```kotlin
        _uiState.value = _uiState.value.copy(overlay = Overlay.Settings, settings = SettingsState())
```

5i. In `dismissOverlay()`, change `copy(overlay = Overlay.None, bitratePickerOpen = false)` to `copy(overlay = Overlay.None, settings = SettingsState())`.

5j. In `previewChannel`, change `delay(Prebuffer.countdownMs(state.prebufferEnabled))` to `delay(Prebuffer.countdownMs(state.prebufferEnabled, state.countdownSettingMs))` and `delay(Prebuffer.DEFAULT_START_DELAY_MS)` to `delay(state.prebufferDelayMs)`.

5k. Add `import app.tellyfin.androidtv.diagnostics.CrashReporting` if it is not already imported (check with `grep -n "import app.tellyfin.androidtv.diagnostics.CrashReporting" PlayerViewModel.kt`).

- [ ] **Step 6: Update `PlayerScreen.kt`.** Replace the whole `SettingsScreen(...)` call inside `state.overlay is Overlay.Settings ->` with:

```kotlin
                SettingsScreen(
                    state = state,
                    context = viewModel.settingsContext,
                    serverUrl = viewModel.jellyfinRepo.baseUrl,
                    appVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.FLAVOR})",
                    modifier = Modifier.fillMaxSize()
                )
```

and in the `ChannelBanner(...)` call change `countdownMs = Prebuffer.countdownMs(state.prebufferEnabled),` to `countdownMs = Prebuffer.countdownMs(state.prebufferEnabled, state.countdownSettingMs),`.

- [ ] **Step 7: Verify nothing references the removed state**

Run: `grep -rnw "bitratePickerOpen\|bitratePickerIndex\|SETTINGS_FOCUS_BANDWIDTH\|START_DELAY_MS\|settings_hint" app/src/main`
Expected: no output.

- [ ] **Step 8: Build every variant, run all unit tests and lint**

Run: `./gradlew assembleSentryDirectDebug assembleNoSentryDirectDebug assembleSentryStoreDebug testSentryDirectDebugUnitTest lintSentryDirectDebug -q`
Expected: succeeds with no `e:` lines and no failing tests.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/res/drawable/ic_settings_*.xml app/src/main/res/values/strings.xml app/src/main/res/values-de/strings.xml app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsScreen.kt app/src/main/java/app/tellyfin/androidtv/ui/player/SettingsPages.kt app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerViewModel.kt app/src/main/java/app/tellyfin/androidtv/ui/player/PlayerScreen.kt
git commit -m "Rework Settings into pages with keybinds and advanced options"
```

---

### Task 6: On-device check build

**Files:** none changed.

- [ ] **Step 1: Build a beta APK signed with the debug key** (the release keystore only exists in CI; do not push a `v*` tag — that publishes to tellyfin.app):

```bash
KEYSTORE_PATH=$HOME/.android/debug.keystore KEYSTORE_PASSWORD=android KEY_ALIAS=androiddebugkey KEY_PASSWORD=android \
  ./gradlew assembleSentryDirectRelease -PversionCode=47 -PversionName=2.0.0-beta.4 -q
~/Android/Sdk/build-tools/37.0.0/aapt2 dump badging app/build/outputs/apk/sentryDirect/release/app-sentry-direct-release.apk | head -1
```

Expected: `versionCode='47' versionName='2.0.0-beta.4'`.

- [ ] **Step 2: Hand it to the user** — only upload it (e.g. `curl https://up.sb -T <apk>`) if the user asks; report the on-device checklist:
  1. Settings opens on the rail; Up/Down switch pages, Right/OK enters, Left/Back returns, Back on the rail closes.
  2. Remote & Keybinds: add RED to Live guide, close Settings, RED opens the guide; GUIDE still does too.
  3. Capture refuses INFO ("INFO already opens Channel info.") and D-pad ("That button can't be used.").
  4. Advanced: set Countdown to 7 s → the switch banner counts from 7; Restore defaults puts it back to Auto and removes RED.
  5. Volume still works while Settings is open.
