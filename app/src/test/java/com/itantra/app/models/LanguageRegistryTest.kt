package com.itantra.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRegistryTest {
    @Test fun registryListsTenRequestedLanguagesAndNoUnmeasuredValidatedPacks() {
        assertEquals(setOf("hi", "en", "ta", "bn", "mr", "gu", "kn", "te", "ml", "or"),
            LanguageRegistry.languages.map { it.code }.toSet())
        assertEquals(10, LanguageRegistry.languages.size)
        assertTrue(LanguageRegistry.languages.none { it.status == ModelStatus.VALIDATED })
    }
}
