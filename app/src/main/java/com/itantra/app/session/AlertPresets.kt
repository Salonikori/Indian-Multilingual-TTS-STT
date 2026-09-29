package com.itantra.app.session

data class AlertPreset(val id: String, val languageCode: String, val text: String)

/** Resource-backed defaults live in res/values/alert_presets.xml; this fallback is explicit. */
object AlertPresets {
    val defaults = listOf(
        AlertPreset("help_hi", "hi", "आपातकाल है, मुझे मदद चाहिए।"),
        AlertPreset("stop_hi", "hi", "कृपया तुरंत रुकें।"),
        AlertPreset("location_hi", "hi", "मुझे सहायता चाहिए, कृपया यहाँ आएँ।"),
        AlertPreset("danger_hi", "hi", "सावधान, यहाँ खतरा है।"),
        AlertPreset("help_en", "en", "Emergency, I need help."),
        AlertPreset("stop_en", "en", "Please stop immediately."),
        AlertPreset("location_en", "en", "I need assistance. Please come here."),
        AlertPreset("danger_en", "en", "Warning, there is danger here.")
    )
}
