package com.example

import com.example.util.AutonomousScreenAgent
import com.example.util.DeviceAction
import com.example.util.DeviceActionParser
import com.example.utils.TTSManager
import org.junit.Assert.*
import org.junit.Test

class LiveVoiceAutomationTests {

    @Test
    fun testYouTubePlayFirstVideoCommand() {
        val prompt = "youtube par song search kar ke pahele wali video ko play kar do"
        val isAutonomous = AutonomousScreenAgent.isAutonomousDeviceTask(prompt)
        assertTrue("Prompt should be detected as autonomous device task", isAutonomous)

        val directActions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should extract direct actions", directActions.isNotEmpty())
        assertTrue(
            "Should have YouTubePlayAndSubscribe or YouTubeControl",
            directActions.any { it is DeviceAction.YouTubePlayAndSubscribe || it is DeviceAction.YouTubeControl || it is DeviceAction.YouTubeSearch }
        )
    }

    @Test
    fun testYouTubeOpenAndSubscribeCompoundFlow() {
        val prompt = "is video ko open kar ke neeche scroll kar ke subscribe karo"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should extract actions from compound prompt", actions.isNotEmpty())
        assertTrue("Should contain first video or subscribe or scroll", actions.any {
            it is DeviceAction.YouTubeControl || it is DeviceAction.YouTubePlayAndSubscribe || it is DeviceAction.AccessibilityScroll
        })
    }

    @Test
    fun testCallPapaCommand() {
        val prompt = "papa ko call lagao"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse call command", actions.isNotEmpty())
        val callAction = actions.firstOrNull { it is DeviceAction.DialPhone } as? DeviceAction.DialPhone
        assertNotNull("Should have DialPhone action", callAction)
        assertEquals("papa", callAction!!.numberOrName.lowercase())
        assertTrue("Should be direct call", callAction.directCall)
    }

    @Test
    fun testWhatsAppMessageCommand() {
        val prompt = "watsapp par kisi ko hi likh kar bhejo"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse WhatsApp command", actions.isNotEmpty())
        val waAction = actions.firstOrNull { it is DeviceAction.WhatsAppMessage } as? DeviceAction.WhatsAppMessage
        assertNotNull("Should have WhatsAppMessage action", waAction)
        assertEquals("Hii", waAction!!.message)
    }

    @Test
    fun testReadMessagesAndNotificationsCommand() {
        val prompt = "massage abhi kya kya aaye hai"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse message read request", actions.isNotEmpty())
        assertTrue("Should have ReadNotifications", actions.any { it is DeviceAction.ReadNotifications })
    }

    @Test
    fun testHomeScreenNavigationCommand() {
        val prompt = "ab home screen par aao"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse home navigation", actions.isNotEmpty())
        val navAction = actions.firstOrNull { it is DeviceAction.AccessibilityNav } as? DeviceAction.AccessibilityNav
        assertNotNull("Should have AccessibilityNav action", navAction)
        assertEquals("home", navAction!!.navType)
    }

    @Test
    fun testScrollDownCommand() {
        val prompt = "scroll karo"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse scroll command", actions.isNotEmpty())
        val scrollAction = actions.firstOrNull { it is DeviceAction.AccessibilityScroll } as? DeviceAction.AccessibilityScroll
        assertNotNull("Should have AccessibilityScroll action", scrollAction)
        assertEquals("down", scrollAction!!.direction)
    }

    @Test
    fun testSearchContinueCommand() {
        val prompt = "ab koi song search karo search bar kholo"
        val actions = DeviceActionParser.parseFromUserPrompt(prompt)
        assertTrue("Should parse search command", actions.isNotEmpty())
        assertTrue("Should have YouTubeSearch action", actions.any { it is DeviceAction.YouTubeSearch })
    }

    @Test
    fun testTTSManagerNeverSpeaksRawSourceCode() {
        val kotlinCode = """
            package com.example.ui
            import androidx.compose.material3.Text
            class MainActivity {
                fun onCreate() {
                    println("Hello World")
                }
            }
        """.trimIndent()
        val speech = TTSManager.cleanTextForSpeech(kotlinCode)
        assertFalse("TTS must not read raw package or syntax", speech.contains("package com.example"))
        assertFalse("TTS must not read curly braces or function syntax", speech.contains("println"))
        assertEquals("Aapka code generate kar diya hai sir.", speech)
    }
}
