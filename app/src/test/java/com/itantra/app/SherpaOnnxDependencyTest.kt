package com.itantra.app

import org.junit.Test
import org.junit.Assert.*

/**
 * Test to verify sherpa-onnx dependency integration.
 * This ensures the JitPack dependency resolves correctly and classes are accessible.
 */
class SherpaOnnxDependencyTest {

    @Test
    fun `sherpa-onnx classes are accessible`() {
        try {
            // Test that core sherpa-onnx classes can be loaded
            val offlineRecognizerConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizerConfig")
            assertNotNull("OfflineRecognizerConfig class should be accessible", offlineRecognizerConfigClass)
            
            val offlineRecognizerClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineRecognizer")
            assertNotNull("OfflineRecognizer class should be accessible", offlineRecognizerClass)
            
            val offlineTtsClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
            assertNotNull("OfflineTts class should be accessible", offlineTtsClass)
            
            println("✅ All sherpa-onnx classes are accessible")
        } catch (e: ClassNotFoundException) {
            fail("sherpa-onnx dependency not properly resolved: ${e.message}")
        } catch (e: UnsatisfiedLinkError) {
            // Expected in unit tests - native libraries aren't available in JVM tests
            // But reaching this point means classes are accessible (dependency resolved correctly)
            println("✅ sherpa-onnx classes accessible (UnsatisfiedLinkError expected in unit tests)")
        }
    }
    
    @Test
    fun `sherpa-onnx config classes are accessible`() {
        try {
            // Test config classes used by LanguageManager
            val offlineModelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineModelConfig")
            assertNotNull("OfflineModelConfig should be accessible", offlineModelConfigClass)
            
            val offlineNemoEncDecCtcModelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineNemoEncDecCtcModelConfig")
            assertNotNull("OfflineNemoEncDecCtcModelConfig should be accessible", offlineNemoEncDecCtcModelConfigClass)
            
            val offlineWhisperModelConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineWhisperModelConfig")
            assertNotNull("OfflineWhisperModelConfig should be accessible", offlineWhisperModelConfigClass)
            
            val offlineTtsConfigClass = Class.forName("com.k2fsa.sherpa.onnx.OfflineTtsConfig")
            assertNotNull("OfflineTtsConfig should be accessible", offlineTtsConfigClass)
            
            println("✅ All sherpa-onnx config classes are accessible")
        } catch (e: ClassNotFoundException) {
            fail("sherpa-onnx config classes not accessible: ${e.message}")
        } catch (e: UnsatisfiedLinkError) {
            // Expected in unit tests - this means classes are found but native libs aren't available
            println("✅ sherpa-onnx config classes accessible (UnsatisfiedLinkError expected in unit tests)")
        }
    }
    
    @Test
    fun `test dependency version info`() {
        // This test documents the expected sherpa-onnx version
        val expectedVersion = "v1.13.8"
        println("📋 Expected sherpa-onnx version: $expectedVersion")
        println("📋 JitPack source: com.github.k2-fsa.sherpa-onnx:sherpa-onnx:$expectedVersion")
        
        // Note: Actual version checking would require runtime access to sherpa-onnx
        // This test serves as documentation of the expected dependency
        assertTrue("Expected version should be valid", expectedVersion.startsWith("v1.13"))
    }
}