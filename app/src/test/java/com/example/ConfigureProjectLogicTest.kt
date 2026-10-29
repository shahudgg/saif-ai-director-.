package com.example

import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import com.example.ui.components.availableLanguages
import com.example.ui.components.cropSquareBitmap
import com.example.ui.components.minSdkOptions
import com.example.ui.components.targetSdkOptions
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ConfigureProjectLogicTest {

    @Test
    fun testPackageNameDerivation() {
        val appName1 = "New Project"
        val slug1 = appName1.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val pkg1 = "com.saifai.${if (slug1.isEmpty()) "newproject" else slug1}"
        assertEquals("com.saifai.newproject", pkg1)

        val appName2 = "Super Calculator 2.0"
        val slug2 = appName2.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")
        val pkg2 = "com.saifai.${if (slug2.isEmpty()) "newproject" else slug2}"
        assertEquals("com.saifai.supercalculator20", pkg2)
    }

    @Test
    fun testLanguagesOptions() {
        // Verify default language is Java as requested
        val defaultLang = availableLanguages.first()
        assertEquals("Java", defaultLang.name)

        // Verify other requested languages are present
        val langNames = availableLanguages.map { it.name }
        assertTrue(langNames.contains("Kotlin"))
        assertTrue(langNames.contains("Python"))
        assertTrue(langNames.contains("HTML / Web"))
        assertTrue(langNames.contains("C / C++"))
    }

    @Test
    fun testSdkDefaults() {
        val defaultMinSdk = minSdkOptions.first { it.isRecommended }.apiLevel
        val defaultTargetSdk = targetSdkOptions.first { it.isRecommended }.apiLevel

        assertEquals(24, defaultMinSdk)
        assertEquals(34, defaultTargetSdk)
    }

    @Test
    fun testSquareBitmapCropper() {
        // Create sample 400x300 bitmap
        val src = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
        
        // Execute cropSquareBitmap
        val cropped = cropSquareBitmap(
            sourceBitmap = src,
            cropSizePx = 270f,
            panOffset = Offset.Zero,
            userZoom = 1.2f,
            rotationDegrees = 90f,
            isFlipped = true,
            outputSize = 512
        )

        assertNotNull(cropped)
        assertEquals(512, cropped.width)
        assertEquals(512, cropped.height)
    }
}
