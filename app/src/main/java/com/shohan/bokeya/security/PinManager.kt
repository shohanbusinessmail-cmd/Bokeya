package com.shohan.bokeya.security

import android.content.Context
import android.content.SharedPreferences
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Stores the app-lock PIN as a salted PBKDF2 hash.
 *
 * The PIN is never persisted in clear text, and comparison is constant-time so
 * a wrong PIN cannot be discovered by timing. A 4-6 digit PIN is inherently
 * low-entropy, so this is defence against casual snooping (the actual threat
 * model for a phone left on a table), not against forensic extraction.
 */
class PinManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val hasPin: Boolean
        get() = prefs.getString(KEY_HASH, null) != null

    fun setPin(pin: String) {
        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val hash = hash(pin, salt)
        prefs.edit()
            .putString(KEY_SALT, salt.toHex())
            .putString(KEY_HASH, hash.toHex())
            .putInt(KEY_FAILED, 0)
            .apply()
    }

    fun verify(pin: String): Boolean {
        val saltHex = prefs.getString(KEY_SALT, null) ?: return false
        val expectedHex = prefs.getString(KEY_HASH, null) ?: return false
        val actual = hash(pin, saltHex.fromHex())
        val matches = constantTimeEquals(actual.toHex(), expectedHex)
        prefs.edit().putInt(KEY_FAILED, if (matches) 0 else failedAttempts + 1).apply()
        return matches
    }

    fun clearPin() {
        prefs.edit().remove(KEY_SALT).remove(KEY_HASH).remove(KEY_FAILED).apply()
    }

    val failedAttempts: Int
        get() = prefs.getInt(KEY_FAILED, 0)

    private fun hash(pin: String, salt: ByteArray): ByteArray = runCatching {
        val spec = PBEKeySpec(pin.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    }.getOrElse {
        // PBKDF2WithHmacSHA256 is unavailable on some very old devices.
        MessageDigest.getInstance("SHA-256").digest(salt + pin.toByteArray())
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private fun String.fromHex(): ByteArray =
        chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    companion object {
        private const val PREFS_NAME = "bokeya_lock"
        private const val KEY_SALT = "pin_salt"
        private const val KEY_HASH = "pin_hash"
        private const val KEY_FAILED = "failed_attempts"
        private const val SALT_BYTES = 16
        private const val ITERATIONS = 12_000
        private const val KEY_LENGTH_BITS = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"

        const val MIN_PIN_LENGTH = 4
        const val MAX_PIN_LENGTH = 6
    }
}
