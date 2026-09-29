package com.itantra.app.models

import java.io.File
import java.security.MessageDigest

/** SHA-256 integrity check for an externally supplied model-pack file. */
object PackIntegrity {
    private val sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun sha256(file: File): String {
        require(file.isFile) { "Pack file does not exist: ${file.path}" }
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** expectedSha256 must come from a trusted release manifest/channel, not the same untrusted file. */
    fun verify(file: File, expectedSha256: String): Boolean {
        require(sha256Pattern.matches(expectedSha256)) { "Expected SHA-256 must be 64 hexadecimal characters." }
        return MessageDigest.isEqual(
            sha256(file).lowercase().toByteArray(Charsets.US_ASCII),
            expectedSha256.lowercase().toByteArray(Charsets.US_ASCII)
        )
    }
}
