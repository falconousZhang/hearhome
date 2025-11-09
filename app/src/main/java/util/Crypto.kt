package com.example.hearhome.util

import java.security.MessageDigest

object Crypto {
    /**
     * Hashes a string using SHA-256.
     */
    fun sha256(input: String): String {
        val bytes = input.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("") { str, it -> str + "%02x".format(it) }
    }
}