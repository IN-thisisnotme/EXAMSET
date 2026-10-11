package com.example

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import com.example.model.PaperMetadata
import com.example.ui.components.HeaderBanner
import com.example.ui.theme.ExamSetTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class GreetingScreenshotTest {

  @get:Rule val composeTestRule = createComposeRule()

  @Test
  fun exam_header_screenshot() {
    composeTestRule.setContent {
      ExamSetTheme {
        HeaderBanner(
          metadata = PaperMetadata(
            title = "All India Science Olympiad 2026",
            subject = "General Science",
            exam = "National Level Set-A"
          ),
          totalQuestions = 50
        )
      }
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/exam_header.png")
  }
}
