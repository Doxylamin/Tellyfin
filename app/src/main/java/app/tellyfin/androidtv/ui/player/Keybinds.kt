package app.tellyfin.androidtv.ui.player

import android.view.KeyEvent

/**
 * App-internal "open the quick menu" signal. No remote button sends it: holding OK does, and so
 * does a button the user added for Quick menu. Negative so it can never collide with a real
 * keycode. Outside playback it means MENU (see [routeQuickMenu]).
 */
const val KEYCODE_QUICK_MENU = -1001

/**
 * Remote actions that can take one extra, user-chosen button on top of their standard ones
 * (for remotes that lack a button or send an unusual keycode). [keyCode] is what an added button
 * stands in for; [defaultKeyCodes] are the remote buttons that already do it. Declaration order
 * is the order Settings lists them in.
 */
enum class KeyAction(val keyCode: Int, val defaultKeyCodes: List<Int>) {
    QUICK_MENU(KEYCODE_QUICK_MENU, emptyList()),
    CHANNEL_INFO(KeyEvent.KEYCODE_INFO, listOf(KeyEvent.KEYCODE_INFO)),
    LIVE_GUIDE(KeyEvent.KEYCODE_GUIDE, listOf(KeyEvent.KEYCODE_GUIDE, KeyEvent.KEYCODE_MENU)),
    SEARCH(KeyEvent.KEYCODE_SEARCH, listOf(KeyEvent.KEYCODE_SEARCH)),
    CHANNEL_UP(KeyEvent.KEYCODE_CHANNEL_UP, listOf(KeyEvent.KEYCODE_CHANNEL_UP)),
    CHANNEL_DOWN(KeyEvent.KEYCODE_CHANNEL_DOWN, listOf(KeyEvent.KEYCODE_CHANNEL_DOWN))
}

/**
 * The quick menu only exists while watching. Everywhere else, holding OK (or a Quick-menu button)
 * keeps doing what it always did there: stand in for MENU on remotes that don't have one.
 */
fun routeQuickMenu(keyCode: Int, inPlayer: Boolean): Int =
    if (keyCode == KEYCODE_QUICK_MENU && !inPlayer) KeyEvent.KEYCODE_MENU else keyCode

sealed interface CaptureResult {
    data object Accepted : CaptureResult
    data object Reserved : CaptureResult
    data class Conflict(val action: KeyAction) : CaptureResult
}

/**
 * Buttons the app depends on (navigation, digits) or the system owns (volume, power, home), plus
 * UNKNOWN: remotes that report all their odd buttons as UNKNOWN would otherwise fire one action
 * from every one of them.
 */
val RESERVED_KEYS: Set<Int> = setOf(
    KeyEvent.KEYCODE_UNKNOWN,
    KEYCODE_QUICK_MENU,  // holding OK while the capture dialog waits
    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_LEFT,
    KeyEvent.KEYCODE_DPAD_RIGHT, KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_BACK,
    KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN, KeyEvent.KEYCODE_VOLUME_MUTE,
    KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME
) + (KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9)

data class Keybinds(val extras: Map<KeyAction, Int> = emptyMap()) {

    /** A user-added button becomes its action's standard keycode; anything else passes through. */
    fun resolve(keyCode: Int): Int =
        extras.entries.firstOrNull { it.value == keyCode }?.key?.keyCode ?: keyCode

    fun validate(action: KeyAction, keyCode: Int): CaptureResult {
        if (keyCode in RESERVED_KEYS) return CaptureResult.Reserved
        val owner = KeyAction.entries.firstOrNull { keyCode in it.defaultKeyCodes }
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
