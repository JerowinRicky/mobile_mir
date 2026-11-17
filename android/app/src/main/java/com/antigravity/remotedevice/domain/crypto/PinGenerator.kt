package com.antigravity.remotedevice.domain.crypto

import com.antigravity.remotedevice.domain.model.GeneratedPin
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class PinGenerator(
    private val ttlSeconds: Long = 300L,
    private val iterations: Int = 10000
) {
    private val secureRandom = SecureRandom()

    /**
     * Generates a cryptographically secure 6-digit temporary pairing PIN with PBKDF2 hash.
     */
    fun generate(): GeneratedPin {
        // Securely select a random number between 100000 and 999999
        val number = 100000 + secureRandom.nextInt(900000)
        val rawPin = number.toString()
        val formattedPin = "${rawPin.substring(0, 3)}-${rawPin.substring(3)}"

        // Generate 16-byte random salt
        val saltBytes = ByteArray(16)
        secureRandom.nextBytes(saltBytes)
        val saltHex = bytesToHex(saltBytes)

        // Compute PBKDF2 hash of the PIN
        val pinHash = hashPin(rawPin, saltBytes, iterations)

        val now = System.currentTimeMillis()
        val expiresAt = now + (ttlSeconds * 1000L)

        return GeneratedPin(
            rawPin = rawPin,
            formattedPin = formattedPin,
            salt = saltHex,
            pinHash = pinHash,
            createdAtMs = now,
            expiresAtMs = expiresAt
        )
    }

    /**
     * Verifies if a given raw PIN matches the stored PBKDF2 hash.
     */
    fun verifyPin(rawPin: String, saltHex: String, expectedHash: String): Boolean {
        val saltBytes = hexToBytes(saltHex)
        val computedHash = hashPin(rawPin, saltBytes, iterations)
        return constantTimeEquals(computedHash, expectedHash)
    }

    private fun hashPin(pin: String, salt: ByteArray, iterations: Int): String {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, 256)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hashBytes = factory.generateSecret(spec).encoded
        return bytesToHex(hashBytes)
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }

    private fun hexToBytes(hex: String): ByteArray {
        val len = hex.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(hex[i], 16) shl 4) + Character.digit(hex[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }
}
