// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.kotlin.compose) apply false
  alias(libs.plugins.google.devtools.ksp) apply false
  alias(libs.plugins.roborazzi) apply false
  alias(libs.plugins.secrets) apply false
  // [DEPRECATED 2026-11-26] Reason: Firebase AI / App Check removed in favor of 100% offline SpeechRecognizer comparison. Restore if: Firebase products are re-enabled.
  // alias(libs.plugins.google.services) apply false
}
