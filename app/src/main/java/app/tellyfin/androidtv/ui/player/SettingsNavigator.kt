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
