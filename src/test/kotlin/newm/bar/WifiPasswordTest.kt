package newm.bar

import dev.caulis.core.dbus.Variant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A wrong Wi-Fi password replaced in the profile: NetworkManager keeps the new one itself. */
class WifiPasswordTest {
    private fun profile(vararg security: Pair<String, Variant>): MutableMap<String, MutableMap<String, Variant>> = linkedMapOf(
        "connection" to linkedMapOf("id" to Variant("s", "Home")),
        "802-11-wireless-security" to linkedMapOf(*security),
    )

    @Test
    fun `a password a secret agent kept is stored by NetworkManager now`() {
        // As nm-applet, plasma-nm and GNOME make a profile: psk-flags 1, the agent keeps the password.
        val sections = profile("key-mgmt" to Variant("s", "wpa-psk"), "psk-flags" to Variant("u", 1))
        assertTrue(withPassword(sections, WifiSecurity.PSK, "new password"))
        val security = sections.getValue("802-11-wireless-security")
        assertEquals("new password", security.getValue("psk").value)
        assertEquals("u", security.getValue("psk-flags").signature)
        assertEquals(0, (security.getValue("psk-flags").value as Number).toInt())
        assertEquals("Home", sections.getValue("connection").getValue("id").value, "the rest of the profile stays")
    }

    @Test
    fun `WEP too, and a profile without a security part gets a new one`() {
        val wep = profile("key-mgmt" to Variant("s", "none"), "wep-key-flags" to Variant("u", 1))
        assertTrue(withPassword(wep, WifiSecurity.WEP, "abcde"))
        assertEquals(0, (wep.getValue("802-11-wireless-security").getValue("wep-key-flags").value as Number).toInt())
        val none = linkedMapOf<String, MutableMap<String, Variant>>()
        assertTrue(withPassword(none, WifiSecurity.SAE, "pw"))
        assertEquals("sae", none.getValue("802-11-wireless-security").getValue("key-mgmt").value)
        assertFalse(withPassword(linkedMapOf(), WifiSecurity.ENTERPRISE, "pw"))
    }
}
