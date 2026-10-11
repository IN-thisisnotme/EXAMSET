package com.example.util

open class FeatureTogglesDefaults {
    open val ENABLE_CAMERA_NOTES_SCANNER: Boolean get() = false
}

/**
 * Global Feature Flags & Architecture Toggles.
 *
 * TO DISABLE OR RE-ENABLE THE ENTIRE CAMERA & HANDWRITTEN NOTES OCR PIPELINE:
 * Comment or uncomment the single line below (or set it to false)!
 */
object FeatureToggles : FeatureTogglesDefaults() {

    // --------------------------------------------------------------------------
    // SINGLE LINE FEATURE TOGGLE:
    // Change to false (or comment out) to completely deactivate the feature.
    override val ENABLE_CAMERA_NOTES_SCANNER: Boolean = true
    // --------------------------------------------------------------------------

    /**
     * Helper to safely query feature availability across UI and viewmodels.
     */
    fun isCameraNotesScannerActive(): Boolean {
        return ENABLE_CAMERA_NOTES_SCANNER
    }
}

