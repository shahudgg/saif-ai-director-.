package com.example

import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Smoke test only: no claim that installation, terminal or agent services work. */
@RunWith(AndroidJUnit4::class)
class EmbeddedEngineSmokeTest {
    @Test
    fun originalScreenStartsInsideHostAndAssetsAreBundled() {
        assumeTrue("Only available in the opt-in integration build", BuildConfig.EMBEDDED_ENGINE_EXPERIMENT)
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val componentName = "com.example.gemini.MainActivity"
        val info = target.packageManager.getActivityInfo(
            android.content.ComponentName(target.packageName, componentName), 0
        )
        assertEquals(target.packageName, info.packageName)
        assertTrue("Engine screen must not be exported", !info.exported)
        val bridge = target.assets.open("bin/agy_ide_bridge").use { it.readNBytes(4) }
        assertTrue(bridge.contentEquals(byteArrayOf(0x7f, 0x45, 0x4c, 0x46)))
        target.assets.open("licenses/ANTIGEM_GPL-3.0.txt").close()
        val intent = Intent().setClassName(target.packageName, componentName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ActivityScenario.launch<android.app.Activity>(intent).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }
}
