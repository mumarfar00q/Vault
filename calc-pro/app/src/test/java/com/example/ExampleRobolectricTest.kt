package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Calc Pro", appName)
  }

  @Test
  fun `evaluate basic operations`() {
    val evaluator = MathEvaluator(isDegree = true)
    // 3 + 4 * 2 - 5 = 6
    assertEquals(6.0, evaluator.evaluate("3 + 4 × 2 − 5"), 1e-9)
    // 12 / 4 = 3
    assertEquals(3.0, evaluator.evaluate("12 ÷ 4"), 1e-9)
  }

  @Test
  fun `evaluate postfix percentages and powers`() {
    val evaluator = MathEvaluator(isDegree = true)
    // 50% = 0.5
    assertEquals(0.5, evaluator.evaluate("50%"), 1e-9)
    // 5² = 25
    assertEquals(25.0, evaluator.evaluate("5²"), 1e-9)
    // 2³ = 8
    assertEquals(8.0, evaluator.evaluate("2³"), 1e-9)
  }

  @Test
  fun `evaluate trigonometry in degrees and radians`() {
    val degreeEvaluator = MathEvaluator(isDegree = true)
    // sin(30) in degree = 0.5
    assertEquals(0.5, degreeEvaluator.evaluate("sin(30)"), 1e-9)
    
    val radianEvaluator = MathEvaluator(isDegree = false)
    // cos(0) = 1.0
    assertEquals(1.0, radianEvaluator.evaluate("cos(0)"), 1e-9)
  }

  @Test
  fun `evaluate implicit multiplication`() {
    val evaluator = MathEvaluator(isDegree = true)
    // 5(3 + 2) = 25
    assertEquals(25.0, evaluator.evaluate("5(3 + 2)"), 1e-9)
    // 2sin(30) = 1.0
    assertEquals(1.0, evaluator.evaluate("2sin(30)"), 1e-9)
  }

  @Test
  fun `evaluate auto-balance parentheses`() {
    val evaluator = MathEvaluator(isDegree = true)
    // sin(30 should auto balance to sin(30)
    assertEquals(0.5, evaluator.evaluate("sin(30"), 1e-9)
  }

  @Test
  fun `evaluate division by zero throws arithmetic exception`() {
    val evaluator = MathEvaluator(isDegree = true)
    assertThrows(Exception::class.java) {
      evaluator.evaluate("10 ÷ 0")
    }
  }
}
