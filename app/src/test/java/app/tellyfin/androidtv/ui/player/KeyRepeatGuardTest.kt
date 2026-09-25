package app.tellyfin.androidtv.ui.player

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class KeyRepeatGuardTest {

    private var now = 0L
    private val guard = KeyRepeatGuard(now = { now })
    private val red = 183

    @Test
    fun `a just-bound button held down does not fire its action`() {
        guard.arm(red)
        now += 500   // first auto-repeat
        assertTrue(guard.shouldSwallow(red))
        repeat(40) {  // still held: repeats every 50 ms for 2 s
            now += 50
            assertTrue(guard.shouldSwallow(red))
        }
    }

    @Test
    fun `once released it works normally`() {
        guard.arm(red)
        now += 500
        guard.shouldSwallow(red)
        now += KeyRepeatGuard.QUIET_MS + 1
        assertFalse(guard.shouldSwallow(red))
        assertFalse(guard.shouldSwallow(red), "stays disarmed")
    }

    @Test
    fun `other buttons are never affected`() {
        guard.arm(red)
        assertFalse(guard.shouldSwallow(red + 1))
    }

    @Test
    fun `nothing is swallowed before anything was bound`() {
        assertFalse(guard.shouldSwallow(red))
    }
}
