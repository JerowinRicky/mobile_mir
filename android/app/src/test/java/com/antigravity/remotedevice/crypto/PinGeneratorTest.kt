package com.antigravity.remotedevice.crypto

import com.antigravity.remotedevice.domain.crypto.PinGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PinGeneratorTest {

    private lateinit var pinGenerator: PinGenerator

    @Before
    fun setUp() {
        pinGenerator = PinGenerator(ttlSeconds = 300L, iterations = 1000)
    }

    @Test
    fun `generatePin returns valid 6-digit number and formatted string`() {
        val pin = pinGenerator.generate()

        assertNotNull(pin)
        assertEquals(6, pin.rawPin.length)
        assertTrue(pin.rawPin.all { it.isDigit() })
        assertEquals(7, pin.formattedPin.length) // XXX-XXX
        assertEquals('-', pin.formattedPin[3])
        assertEquals(pin.rawPin.substring(0, 3) + "-" + pin.rawPin.substring(3), pin.formattedPin)
    }

    @Test
    fun `generatePin generates distinct random PINs and salts`() {
        val pin1 = pinGenerator.generate()
        val pin2 = pinGenerator.generate()

        // Salts must always be distinct
        assertFalse(pin1.salt == pin2.salt)
        // Hashes must be distinct
        assertFalse(pin1.pinHash == pin2.pinHash)
    }

    @Test
    fun `verifyPin returns true for correct raw pin and salt`() {
        val pin = pinGenerator.generate()
        val isValid = pinGenerator.verifyPin(pin.rawPin, pin.salt, pin.pinHash)

        assertTrue("Verification should succeed with correct PIN and salt", isValid)
    }

    @Test
    fun `verifyPin returns false for wrong pin`() {
        val pin = pinGenerator.generate()
        val wrongPin = if (pin.rawPin == "123456") "654321" else "123456"
        val isValid = pinGenerator.verifyPin(wrongPin, pin.salt, pin.pinHash)

        assertFalse("Verification must fail with incorrect PIN", isValid)
    }

    @Test
    fun `verifyPin returns false for modified salt`() {
        val pin = pinGenerator.generate()
        val modifiedSalt = pin.salt.reversed()
        val isValid = pinGenerator.verifyPin(pin.rawPin, modifiedSalt, pin.pinHash)

        assertFalse("Verification must fail with tampered salt", isValid)
    }

    @Test
    fun `pin expiration calculation is correct`() {
        val shortLivedGenerator = PinGenerator(ttlSeconds = 2L, iterations = 100)
        val pin = shortLivedGenerator.generate()

        assertFalse(pin.isExpired)
        assertTrue(pin.remainingSeconds in 1..2)
    }
}
