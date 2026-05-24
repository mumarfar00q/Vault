package com.example

import android.app.Application
import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
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
  fun greeting_screenshot() {
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(application)

    composeTestRule.setContent { 
      MyApplicationTheme { 
        ScientificCalculatorScreen(
          viewModel = viewModel,
          onPinCorrect = {},
          onBreakInDetected = {}
        ) 
      } 
    }

    composeTestRule.onRoot().captureRoboImage(filePath = "src/test/screenshots/greeting.png")
  }

  @Test
  fun test_successful_shadow_input_sequence() {
    var pCorrect = false
    var breakIn = false
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(application)

    composeTestRule.setContent {
      MyApplicationTheme {
        ScientificCalculatorScreen(
          viewModel = viewModel,
          onPinCorrect = { pCorrect = true },
          onBreakInDetected = { breakIn = true }
        )
      }
    }

    // Tap "1", "2", "3", "4" -> shadow buffer matches PIN sequence, then operators checks logic
    composeTestRule.onNodeWithTag("btn_1").performClick()
    composeTestRule.onNodeWithTag("btn_2").performClick()
    composeTestRule.onNodeWithTag("btn_3").performClick()
    composeTestRule.onNodeWithTag("btn_4").performClick()
    
    // Tap operator "=" to trigger checks
    composeTestRule.onNodeWithTag("btn_=").performClick()

    assertEquals(true, pCorrect)
    assertEquals(false, breakIn)
  }

  @Test
  fun test_break_in_attempt_logging_sequence() {
    var pCorrect = false
    var breakInCount = 0
    val application = ApplicationProvider.getApplicationContext<Application>()
    val viewModel = MainViewModel(application)

    composeTestRule.setContent {
      MyApplicationTheme {
        ScientificCalculatorScreen(
          viewModel = viewModel,
          onPinCorrect = { pCorrect = true },
          onBreakInDetected = { breakInCount++ }
        )
      }
    }

    // Tapping wrong entry 1: "1", "9", "-"
    composeTestRule.onNodeWithTag("btn_1").performClick()
    composeTestRule.onNodeWithTag("btn_9").performClick()
    composeTestRule.onNodeWithTag("btn_−").performClick() // Minus operator U+2212
    assertEquals(0, breakInCount)

    // Tapping wrong entry 2: "1", "9", "-"
    composeTestRule.onNodeWithTag("btn_1").performClick()
    composeTestRule.onNodeWithTag("btn_9").performClick()
    composeTestRule.onNodeWithTag("btn_−").performClick()
    assertEquals(0, breakInCount)

    // Tapping wrong entry 3: "1", "9", "="
    composeTestRule.onNodeWithTag("btn_1").performClick()
    composeTestRule.onNodeWithTag("btn_9").performClick()
    composeTestRule.onNodeWithTag("btn_=").performClick()
    
    // Should now have triggered breakInCount = 1
    assertEquals(1, breakInCount)
    assertEquals(false, pCorrect)
  }
}
