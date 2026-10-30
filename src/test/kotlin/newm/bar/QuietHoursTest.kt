package newm.bar

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** notifications { quietFrom; quietUntil }: the quiet hours, also past midnight. */
class QuietHoursTest {

    private fun at(t: String) = LocalTime.parse(t)

    @Test
    fun `quiet hours past midnight and within a day`() {
        assertTrue(inQuietHours("22:00", "07:00", at("23:30")))
        assertTrue(inQuietHours("22:00", "07:00", at("03:00")))
        assertTrue(inQuietHours("22:00", "07:00", at("22:00")))
        assertFalse(inQuietHours("22:00", "07:00", at("07:00")))
        assertFalse(inQuietHours("22:00", "07:00", at("12:00")))
        assertTrue(inQuietHours("13:00", "14:30", at("14:00")))
        assertFalse(inQuietHours("13:00", "14:30", at("15:00")))
    }

    @Test
    fun `no quiet hours unless both are times`() {
        assertFalse(inQuietHours("", "07:00", at("03:00")))
        assertFalse(inQuietHours("22:00", "", at("23:00")))
        assertFalse(inQuietHours("late", "07:00", at("03:00")))
        assertFalse(inQuietHours("08:00", "08:00", at("08:00")))
    }
}
