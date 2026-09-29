package com.itantra.app.models

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PackIntegrityTest {
    @Test fun validFileMatchesItsSha256() {
        val file = Files.createTempFile("itantra-pack", ".bin").toFile()
        try {
            file.writeBytes("model-pack-content".toByteArray())
            val expected = PackIntegrity.sha256(file)
            assertTrue(PackIntegrity.verify(file, expected))
        } finally { file.delete() }
    }

    @Test fun oneByteCorruptionIsRejected() {
        val file = Files.createTempFile("itantra-pack", ".bin").toFile()
        try {
            val original = "model-pack-content".toByteArray()
            file.writeBytes(original)
            val trustedDigest = PackIntegrity.sha256(file)
            val corrupted = original.copyOf()
            corrupted[0] = (corrupted[0].toInt() xor 0x01).toByte()
            file.writeBytes(corrupted)
            assertFalse(PackIntegrity.verify(file, trustedDigest))
        } finally { file.delete() }
    }

    @Test fun malformedDigestIsRejected() {
        val file = Files.createTempFile("itantra-pack", ".bin").toFile()
        try {
            file.writeText("data")
            try {
                PackIntegrity.verify(file, "not-a-sha256")
                throw AssertionError("Malformed digest should be rejected")
            } catch (_: IllegalArgumentException) { }
        } finally { file.delete() }
    }
}
