package app.tellyfin.androidtv.data

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class UpdateCheckerTest {

    @Test
    fun `newer patch, minor and major versions are detected`() {
        assertTrue(UpdateChecker.isNewer("1.0.1", "1.0"))
        assertTrue(UpdateChecker.isNewer("1.1", "1.0.9"))
        assertTrue(UpdateChecker.isNewer("2.0", "1.9.9"))
    }

    @Test
    fun `equal and older versions are not newer`() {
        assertFalse(UpdateChecker.isNewer("1.0", "1.0"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.0"))
        assertFalse(UpdateChecker.isNewer("1.0", "1.0.1"))
        assertFalse(UpdateChecker.isNewer("1.9", "2.0"))
    }

    @Test
    fun `v prefix is ignored`() {
        assertTrue(UpdateChecker.isNewer("v1.1", "1.0"))
        assertTrue(UpdateChecker.isNewer("1.1", "v1.0"))
        assertFalse(UpdateChecker.isNewer("v1.0", "v1.0"))
    }

    @Test
    fun `malformed segments are treated as zero`() {
        assertFalse(UpdateChecker.isNewer("garbage", "1.0"))
        assertTrue(UpdateChecker.isNewer("1.0", "garbage"))
    }
}
