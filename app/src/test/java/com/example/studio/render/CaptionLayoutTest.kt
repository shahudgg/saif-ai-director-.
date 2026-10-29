package com.example.studio.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptionLayoutTest {

    /** Fake monospace font: every character is 10 units wide at size 10 (scales linearly). */
    private val measure: (String, Float) -> Float = { s, size -> s.length * size }

    @Test fun `a caption wider than the frame is wrapped`() {
        val text = "Transformer ek neural network architecture hai jo attention use karta hai"
        val lines = CaptionLayout.wrap(text, 200f) { measure(it, 10f) }
        assertTrue(lines.size > 1)
        lines.forEach { assertTrue("line '$it' too wide", measure(it, 10f) <= 200f) }
        assertEquals(text, lines.joinToString(" "))
    }

    @Test fun `a single word longer than the width is hard broken`() {
        val lines = CaptionLayout.wrap("Supercalifragilisticexpialidocious", 100f) { measure(it, 10f) }
        assertTrue(lines.size >= 3)
        lines.forEach { assertTrue(measure(it, 10f) <= 100f) }
    }

    @Test fun `fit shrinks the font before truncating`() {
        val fit = CaptionLayout.fit("one two three four five six seven eight", 240f, maxLines = 2, startSize = 20f, minSize = 8f, measure = measure)
        assertTrue(fit.lines.size <= 2)
        assertTrue(fit.textSize < 20f)
        fit.lines.forEach { assertTrue(measure(it, fit.textSize) <= 240f) }
    }

    @Test fun `fit truncates with an ellipsis only at the minimum size`() {
        val fit = CaptionLayout.fit("a ".repeat(200).trim(), 100f, maxLines = 2, startSize = 20f, minSize = 10f, measure = measure)
        assertEquals(2, fit.lines.size)
        assertTrue(fit.lines.last().endsWith("\u2026"))
    }

    @Test fun `long narration is chunked into short groups that cover the whole duration`() {
        val text = "Aaj hum samjhenge ki AI model kaise kaam karta hai, aur kyun ye itna powerful hai."
        val chunks = CaptionChunker.chunk(text, startUs = 1_000_000L, durationUs = 6_000_000L)
        assertTrue(chunks.size >= 3)
        chunks.forEach { assertTrue("too many words: ${it.text}", it.words.size <= 5) }
        assertEquals(1_000_000L, chunks.first().startUs)
        assertEquals(7_000_000L, chunks.last().endUs)
        chunks.zipWithNext().forEach { (a, b) -> assertEquals(a.endUs, b.startUs) }
        assertEquals(text, chunks.joinToString(" ") { it.text })
    }

    @Test fun `no lonely single word tail`() {
        val chunks = CaptionChunker.chunk("one two three four five", 0, 5_000_000L, minWords = 2, maxWords = 4)
        assertTrue(chunks.all { it.words.size >= 2 })
    }

    @Test fun `active chunk follows time and holds the last one`() {
        val chunks = CaptionChunker.chunk("one two three four five six seven eight", 0, 8_000_000L, maxWords = 4)
        assertEquals(chunks.first(), CaptionChunker.active(chunks, 0))
        assertEquals(chunks.last(), CaptionChunker.active(chunks, 9_000_000L))
    }

    @Test fun `active word index advances inside a chunk`() {
        val c = CaptionChunker.Chunk("alpha beta gamma", 0, 3_000_000L)
        assertEquals(0, CaptionChunker.activeWordIndex(c, 100_000L))
        assertEquals(2, CaptionChunker.activeWordIndex(c, 2_900_000L))
    }
}
