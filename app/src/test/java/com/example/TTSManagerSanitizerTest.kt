package com.example

import com.example.utils.TTSManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TTSManagerSanitizerTest {

    @Test
    fun testStripEmojisFromText() {
        val input = "Hello! Main aapki kya madad kar sakta hoon? 😊✨"
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Hello! Main aapki kya madad kar sakta hoon?", result)
        assertFalse(result.contains("😊"))
        assertFalse(result.contains("✨"))
    }

    @Test
    fun testStripMultipleEmojisAndSymbols() {
        val input = "Shandar car 🚗🔥! Sabse best speed ⚡🚀 aur safe design ❤️👍"
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Shandar car! Sabse best speed aur safe design", result)
    }

    @Test
    fun testImageGenerationWithIntroText() {
        val input = "Maine aapke liye ek sundar car ki image generate kar di hai:\n![Generated Image](https://image.pollinations.ai/prompt/a%20beautiful%20red%20sports%20car?nologo=true&enhance=true&seed=48192) 🚗✨"
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Maine aapke liye ek sundar car ki image generate kar di hai:", result)
        assertFalse(result.contains("Generated Image"))
        assertFalse(result.contains("http"))
        assertFalse(result.contains("pollinations"))
        assertFalse(result.contains("48192"))
    }

    @Test
    fun testOnlyImageMarkdownReturnsFriendlyConfirmation() {
        val input = "![Generated Image](https://image.pollinations.ai/prompt/cute%20cat?nologo=true&enhance=true&seed=12345)"
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Maine aapke liye image generate kar di hai.", result)
    }

    @Test
    fun testCodeBlockOmittedForSpeech() {
        val input = "Yeh raha aapka code:\n```kotlin\nfun main() {\n    println(\"Hello\")\n}\n```\nAap ise run kar sakte hain."
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Yeh raha aapka code: Aap ise run kar sakte hain.", result)
        assertFalse(result.contains("println"))
        assertFalse(result.contains("{"))
    }

    @Test
    fun testMarkdownFormattingStripped() {
        val input = "### Title\n- **Item 1:** *Bahut acha*\n- **Item 2:** _Tez speed_"
        val result = TTSManager.cleanTextForSpeech(input)
        assertEquals("Title Item 1: Bahut acha Item 2: Tez speed", result)
    }
}
