package com.itantra.app.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageRegistryTest {
    @Test fun registryListsOnlyHindiAndEnglishForDepthOverBreadth() {
        assertEquals(setOf("hi", "en"),
            LanguageRegistry.languages.map { it.code }.toSet())
        assertEquals(2, LanguageRegistry.languages.size)
        assertTrue("All languages should have NOT_INSTALLED status initially", 
            LanguageRegistry.languages.all { it.status == ModelStatus.NOT_INSTALLED })
    }
}
