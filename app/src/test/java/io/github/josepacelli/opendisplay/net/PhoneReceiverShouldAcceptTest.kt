package io.github.josepacelli.opendisplay.net

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #154: with no peer authentication on the control/video socket (SECURITY.md/SCR-001), any
 * host that can reach the listener could otherwise silently take over an active session. */
class PhoneReceiverShouldAcceptTest {

    private val mac = InetAddress.getByName("192.168.1.5")
    private val attacker = InetAddress.getByName("192.168.1.99")

    @Test
    fun `accepts any address when idle`() {
        assertTrue(PhoneReceiver.shouldAccept(currentAddress = null, hasActiveSession = false, newAddress = attacker))
    }

    @Test
    fun `accepts a reconnect from the same address`() {
        assertTrue(PhoneReceiver.shouldAccept(currentAddress = mac, hasActiveSession = true, newAddress = mac))
    }

    @Test
    fun `rejects a different address while a session is active`() {
        assertFalse(PhoneReceiver.shouldAccept(currentAddress = mac, hasActiveSession = true, newAddress = attacker))
    }

    @Test
    fun `rejects any TCP address while a USB session is active`() {
        // USB sessions have no network address, so currentAddress is null even though the
        // session is active.
        assertFalse(PhoneReceiver.shouldAccept(currentAddress = null, hasActiveSession = true, newAddress = mac))
    }
}
