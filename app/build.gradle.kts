import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.roborazzi)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.examset.bofvhj"
    minSdk = 24
    targetSdk = 36
    versionCode = 1
    versionName = "1.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
  ignoreList.add("GEMINI_API_KEY")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // [DEPRECATED 2026-11-23] Reason: Firebase BOM/AI/AppCheck are currently unused in app source (OkHttp + ML Kit used directly). Restore if: Firebase AI Logic or App Check is integrated.
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  // [DEPRECATED 2026-11-23] Reason: Moshi and Retrofit are unused in app source (networking uses OkHttp + org.json). Restore if: Retrofit/Moshi API clients are added.
  // implementation(libs.converter.moshi)
  // implementation(libs.firebase.ai)
  // Uncomment to use Firestore:
  // implementation(libs.firebase.firestore)

  // Uncomment ALL FOUR of the following dependencies together to use Firebase Auth and Google
  // Sign-In via Credential Manager:
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  // [DEPRECATED 2026-11-23] Reason: OkHttp HttpLoggingInterceptor is unused in app source. Restore if: debug HTTP logging is enabled.
  // implementation(libs.logging.interceptor)
  // implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  // implementation(libs.retrofit)
  implementation(libs.play.services.mlkit.text.recognition)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  testImplementation(libs.roborazzi)
  testImplementation(libs.roborazzi.compose)
  testImplementation(libs.roborazzi.junit.rule)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  // [DEPRECATED 2026-11-23] Reason: Moshi codegen KSP processor is unused while Moshi is disabled. Restore if: Moshi @JsonClass models are added.
  // "ksp"(libs.moshi.kotlin.codegen)
}

abstract class CopyExamSetApkTask : org.gradle.api.DefaultTask() {
  @get:org.gradle.api.tasks.InputFile
  @get:org.gradle.api.tasks.PathSensitive(org.gradle.api.tasks.PathSensitivity.NONE)
  abstract val sourceApk: org.gradle.api.file.RegularFileProperty

  @get:org.gradle.api.tasks.OutputFile
  abstract val targetApk: org.gradle.api.file.RegularFileProperty

  @org.gradle.api.tasks.TaskAction
  fun copyApk() {
    val src = sourceApk.get().asFile
    if (!src.exists()) {
      throw org.gradle.api.GradleException("Source APK not found at ${src.absolutePath}")
    }
    val dst = targetApk.get().asFile
    val parent = dst.parentFile ?: throw org.gradle.api.GradleException("Invalid target directory for ${dst.absolutePath}")
    parent.mkdirs()

    val tempFile = File.createTempFile("ExamSet.", ".apk.tmp", parent)
    try {
      src.copyTo(tempFile, overwrite = true)
      if (tempFile.length() != src.length()) {
        throw org.gradle.api.GradleException(
          "Copied temp APK size mismatch (${tempFile.length()} != ${src.length()})"
        )
      }
      Files.move(
        tempFile.toPath(),
        dst.toPath(),
        StandardCopyOption.REPLACE_EXISTING
      )
      dst.setLastModified(System.currentTimeMillis())
      if (!dst.exists() || dst.length() != src.length()) {
        throw org.gradle.api.GradleException("Failed to replace ${dst.absolutePath} cleanly")
      }
      logger.lifecycle("ExamSet APK updated: ${dst.absolutePath} (${dst.length()} bytes)")
    } finally {
      if (tempFile.exists()) {
        tempFile.delete()
      }
    }
  }
}

val copyDebugApkToFolder = tasks.register<CopyExamSetApkTask>("copyDebugApkToFolder") {
  description = "Atomically overwrites apk/ExamSet.apk with the newly built debug APK"
  sourceApk.set(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
  targetApk.set(rootProject.layout.projectDirectory.file("apk/ExamSet.apk"))
  mustRunAfter("packageDebug")
  outputs.upToDateWhen { false }
  outputs.cacheIf { false }
}

val copyReleaseApkToFolder = tasks.register<CopyExamSetApkTask>("copyReleaseApkToFolder") {
  description = "Atomically overwrites apk/ExamSet.apk with the newly built release APK"
  sourceApk.set(layout.buildDirectory.file("outputs/apk/release/app-release.apk"))
  targetApk.set(rootProject.layout.projectDirectory.file("apk/ExamSet.apk"))
  mustRunAfter("packageRelease", copyDebugApkToFolder)
  outputs.upToDateWhen { false }
  outputs.cacheIf { false }
}

tasks.matching { it.name == "packageDebug" || it.name == "assembleDebug" }.configureEach {
  finalizedBy(copyDebugApkToFolder)
}

tasks.matching { it.name == "packageRelease" || it.name == "assembleRelease" }.configureEach {
  finalizedBy(copyReleaseApkToFolder)
}
