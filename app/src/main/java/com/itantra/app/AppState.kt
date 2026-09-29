package com.itantra.app

import com.itantra.app.models.LanguageManager

/**
 * Process-wide mutable state shared between activities in the same process.
 *
 * Only [MainActivity] writes [languageManager]; [LiveSttActivity] reads it.
 * This avoids passing large native handles through Intents (which can't carry them)
 * and avoids a full dependency-injection framework for this prototype.
 *
 * Cleared when the user unloads all models or the process restarts.
 */
object AppState {
    /** The currently-loaded [LanguageManager], or null if no language is loaded. */
    @Volatile
    var languageManager: LanguageManager? = null
}
