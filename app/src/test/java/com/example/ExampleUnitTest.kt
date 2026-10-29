package com.example

import com.example.data.remote.GeminiService
import com.example.ui.components.cleanMathAndLatex
import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCleanMathAndLatex_convertsFractionsAndSymbols() {
    val latex = """The value of x is \frac{3}{2} + \sqrt{16} \times 5 = 2 \pm 1"""
    val cleaned = cleanMathAndLatex(latex)
    assertFalse(cleaned.contains("\\frac"))
    assertFalse(cleaned.contains("\\sqrt"))
    assertFalse(cleaned.contains("\\times"))
    assertFalse(cleaned.contains("\\pm"))
    assertTrue(cleaned.contains("(3 / 2)"))
    assertTrue(cleaned.contains("√(16)"))
    assertTrue(cleaned.contains("×"))
    assertTrue(cleaned.contains("±"))
  }

  @Test
  fun testCleanMathAndLatex_stripsInlineDollarSigns() {
    val dollar = '$'
    val latex = "Let ${dollar}x = 10${dollar} and ${dollar}y = 20${dollar}"
    val cleaned = cleanMathAndLatex(latex)
    assertFalse(cleaned.contains("$"))
    assertTrue(cleaned.contains("x = 10"))
    assertTrue(cleaned.contains("y = 20"))
  }

  @Test
  fun testGenerateLocalSmartResponse_mathFormattingHasNoBacktickOperators() {
    val response = GeminiService.generateLocalSmartResponse("15 + 25", "general")
    assertTrue(response.contains("### 🧮 Math Solution"))
    assertTrue(response.contains("40"))
    // Ensure math operators are not enclosed in raw backticks like `+`
    assertFalse(response.contains("`+`"))
    assertFalse(response.contains("`15 + 25`"))
  }
}
