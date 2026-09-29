package com.itantra.app.models

data class LoadedLanguageState(
    val languageCode: String? = null,
    val sttLoaded: Boolean = false,
    val ttsLoaded: Boolean = false,
    val loading: Boolean = false,
    val sttLoadMillis: Long? = null,
    val ttsLoadMillis: Long? = null,
    val error: String? = null
) {
    val isLoaded: Boolean get() = sttLoaded && ttsLoaded
}
