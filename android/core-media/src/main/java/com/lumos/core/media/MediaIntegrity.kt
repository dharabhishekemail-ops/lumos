package com.lumos.core.media

import java.security.MessageDigest

object MediaIntegrity {
    fun sha256Hex(bytes: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-256")
        val dig = md.digest(bytes)
        return dig.joinToString("") { "%02x".format(it) }
    }
}
