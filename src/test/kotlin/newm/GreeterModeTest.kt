package newm

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** newm knows it is greetd's login screen: greetd's socket, or the greeter's user. */
class GreeterModeTest {

    @Test
    fun `greetd's socket makes newm the greeter whatever its user is called`() {
        // Void's greetd runs its greeter as _greeter: greeter_user ("greeter") does not match, the socket does.
        assertTrue(Users.greeterMode("_greeter", "greeter", "/run/greetd-1234.sock"))
    }

    @Test
    fun `without the socket only greeter_user counts`() {
        assertTrue(Users.greeterMode("greeter", "greeter", null))
        assertTrue(Users.greeterMode("_greeter", "_greeter", ""))
        assertFalse(Users.greeterMode("anna", "greeter", null))
        assertFalse(Users.greeterMode(null, "greeter", null))
    }
}
