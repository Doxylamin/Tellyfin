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
            KeyEvent.KEYCODE_VOLUME_MUTE, KeyEvent.KEYCODE_POWER, KeyEvent.KEYCODE_HOME,
            // Remotes that report every odd button as UNKNOWN would otherwise all trigger one action.
            KeyEvent.KEYCODE_UNKNOWN
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
