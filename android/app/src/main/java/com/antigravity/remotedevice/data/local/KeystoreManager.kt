package com.antigravity.remotedevice.data.local

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.PublicKey
import java.security.Signature
import java.security.spec.ECGenParameterSpec

/**
 * Manages asymmetric device identity keys in the hardware-backed Android KeyStore.
 * Keys are generated using EC secp256r1 (NIST P-256) and never leave secure hardware (TEE/StrongBox).
 */
class KeystoreManager {

    companion object {
        private const val ANDROID_KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val KEY_ALIAS_DEVICE_IDENTITY = "antigravity_device_identity_key"
        private const val SIGNATURE_ALGORITHM = "SHA256withECDSA"
    }

    private val keyStore: KeyStore = KeyStore.getInstance(ANDROID_KEYSTORE_PROVIDER).apply {
        load(null)
    }

    /**
     * Retrieves or generates the hardware-backed EC KeyPair for this device.
     */
    fun getOrCreateDeviceKeyPair(): KeyPair {
        if (!keyStore.containsAlias(KEY_ALIAS_DEVICE_IDENTITY)) {
            generateDeviceKeyPair()
        }
        val privateKey = keyStore.getKey(KEY_ALIAS_DEVICE_IDENTITY, null) as PrivateKey
        val certificate = keyStore.getCertificate(KEY_ALIAS_DEVICE_IDENTITY)
        return KeyPair(certificate.publicKey, privateKey)
    }

    /**
     * Generates a new EC KeyPair protected by the Android KeyStore.
     */
    private fun generateDeviceKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE_PROVIDER
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS_DEVICE_IDENTITY,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).apply {
            setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
            setDigests(KeyProperties.DIGEST_SHA256)
            setUserAuthenticationRequired(false) // Device-level identity, not per-prompt
        }.build()

        keyPairGenerator.initialize(parameterSpec)
        return keyPairGenerator.generateKeyPair()
    }

    /**
     * Signs challenge data with the device private key.
     * @param data Raw bytes to sign (e.g. challenge nonce + timestamp)
     * @return Base64-encoded signature
     */
    fun signChallenge(data: ByteArray): String {
        val privateKey = keyStore.getKey(KEY_ALIAS_DEVICE_IDENTITY, null) as PrivateKey
        val signature = Signature.getInstance(SIGNATURE_ALGORITHM).apply {
            initSign(privateKey)
            update(data)
        }
        val signatureBytes = signature.sign()
        return Base64.encodeToString(signatureBytes, Base64.NO_WRAP)
    }

    /**
     * Returns the device public key in Base64 X.509 format for backend registration.
     */
    fun getPublicKeyPem(): String {
        val keyPair = getOrCreateDeviceKeyPair()
        val encoded = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
        return "-----BEGIN PUBLIC KEY-----\n$encoded\n-----END PUBLIC KEY-----"
    }
}
