package newm

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** pam_faillock's settings and records read as pam_faillock counts them. */
class FaillockTest {

    // Tally records (64 bytes: source, reserved, status, time) failed at [times].
    private fun tally(vararg times: Long, status: Int = 1): ByteArray {
        val buffer = ByteBuffer.allocate(64 * times.size).order(ByteOrder.nativeOrder())
        for ((n, t) in times.withIndex()) {
            buffer.putShort(n * 64 + 54, status.toShort())
            buffer.putLong(n * 64 + 56, t)
        }
        return buffer.array()
    }

    @Test
    fun `faillock conf is read with its defaults`() {
        val conf = Faillock.parseConf("# comment\ndeny = 10\nunlock_time = 120\nsilent\n dir=/tmp/x # here\n")
        assertEquals(Faillock.Conf(dir = "/tmp/x", deny = 10, failInterval = 900, unlockTime = 120), conf)
        assertEquals(0L, Faillock.parseConf("unlock_time = never").unlockTime)
        assertEquals(Faillock.Conf(), Faillock.parseConf(""))
    }

    @Test
    fun `ten wrong passwords lock the account for unlock_time, fewer say how many are left`() {
        val conf = Faillock.Conf(deny = 10, unlockTime = 120)
        val now = 1_000_000L
        val nine = Faillock.state(tally(*LongArray(9) { now - 60 + it }), conf, now)
        assertFalse(nine.locked)
        assertEquals(9, nine.failures)
        assertEquals(1, nine.triesLeft)

        val ten = Faillock.state(tally(*LongArray(10) { now - 60 + it }), conf, now)
        assertTrue(ten.locked)
        // Locked until the latest failure (now - 51) + 120.
        assertEquals(now - 51 + 120, ten.lockedUntil)
        assertEquals("1:09", Faillock.countdown(ten.lockedUntil - now))

        // Once unlock_time has passed: unlocked, and the next wrong one counts from 1 again.
        val later = Faillock.state(tally(*LongArray(10) { now - 600 + it }), conf, now)
        assertFalse(later.locked)
        assertEquals(10, later.triesLeft)

        // Records no longer valid (reset) count for nothing; unlock_time = never stays locked.
        assertEquals(0, Faillock.state(tally(now - 5, status = 0), conf, now).failures)
        assertEquals(-1L, Faillock.state(tally(*LongArray(10) { now - 9000 + it }), conf.copy(unlockTime = 0), now).lockedUntil)
    }
}
