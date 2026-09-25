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
    fun `volume still works while waiting for a button to bind`() {
        val capturing = SettingsState(page = SettingsPage.KEYBINDS, inPane = true, row = 2, capture = CaptureState(KeyAction.LIVE_GUIDE))
        val result = press(capturing, KeyEvent.KEYCODE_VOLUME_UP)
        assertFalse(result.handled)
        assertEquals(CaptureMessage.Reserved, result.state.capture?.message)
        assertNull(result.command)
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
