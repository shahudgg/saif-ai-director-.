package com.example.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FileDownload
import com.example.util.ImageDownloader
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.SaifTheme
import com.example.ui.theme.PrimaryAccent
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.SubtextMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed class MarkdownElement {
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class Paragraph(val text: String) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class Image(val altText: String, val url: String) : MarkdownElement()
}

@Composable
fun MarkdownRenderer(
    content: String,
    modifier: Modifier = Modifier
) {
    val elements = remember(content) { parseMarkdown(content) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    if (selectedImageUrl != null) {
        Dialog(
            onDismissRequest = { selectedImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = true)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
            ) {
                AsyncImage(
                    model = selectedImageUrl,
                    contentDescription = "Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                
                IconButton(
                    onClick = { selectedImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
                

            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Header -> {
                    val fontSize = when (element.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = buildStyledText(element.text),
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = SaifTheme.colors.textPrimary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownElement.BulletItem -> {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 4.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp, end = 8.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(PrimaryAccent)
                        )
                        Text(
                            text = buildStyledText(element.text),
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            color = SaifTheme.colors.textPrimary
                        )
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockCard(language = element.language, code = element.code)
                }
                                is MarkdownElement.Image -> {
                    AsyncImage(
                        model = element.url,
                        contentDescription = element.altText,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 400.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedImageUrl = element.url }
                    )
                }
                is MarkdownElement.Paragraph -> {
                    if (element.text.isNotBlank()) {
                        Text(
                            text = buildStyledText(element.text),
                            fontSize = 14.5.sp,
                            lineHeight = 22.sp,
                            color = SaifTheme.colors.textPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeBlockCard(
    language: String,
    code: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var copied by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = SaifTheme.colors.codeBackground,
        border = androidx.compose.foundation.BorderStroke(1.dp, SaifTheme.colors.codeBorder),
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Column {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF161B22))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF38BDF8))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (language.isNotBlank()) language.uppercase() else "CODE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 0.5.sp
                    )
                }

                // Copy button
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Code snippet", code)
                            clipboard.setPrimaryClip(clip)
                            copied = true
                            Toast.makeText(context, "Code copied to clipboard", Toast.LENGTH_SHORT).show()
                            scope.launch {
                                delay(2000)
                                copied = false
                            }
                        }
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (copied) Icons.Default.Check else Icons.Default.ContentCopy,
                        contentDescription = "Copy code",
                        tint = if (copied) StatusSuccess else SubtextMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (copied) "Copied" else "Copy",
                        fontSize = 11.sp,
                        color = if (copied) StatusSuccess else SubtextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Code Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                Text(
                    text = code,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.5.sp,
                    lineHeight = 18.sp,
                    color = SaifTheme.colors.codeText
                )
            }
        }
    }
}

fun cleanMathAndLatex(raw: String): String {
    if (!raw.contains("\\") && !raw.contains("$")) return raw

    var text = raw

    // 1. Remove display math delimiters \[ and \] and $$
    text = text.replace("\\[", "").replace("\\]", "")
    text = text.replace("$$", "")

    // 2. Unescape \( and \)
    text = text.replace("\\(", "(").replace("\\)", ")")

    // 3. Convert \frac{a}{b} iteratively
    var prev = ""
    var loopCount = 0
    while (text != prev && text.contains("\\frac") && loopCount < 8) {
        prev = text
        text = text.replace(Regex("""\\frac\{([^{}]+)\}\{([^{}]+)\}""")) { match ->
            val num = match.groupValues[1].trim()
            val den = match.groupValues[2].trim()
            "($num / $den)"
        }
        loopCount++
    }

    // 4. Convert \sqrt{x}
    text = text.replace(Regex("""\\sqrt\{([^{}]+)\}""")) { "√(${it.groupValues[1].trim()})" }
    text = text.replace(Regex("""\\sqrt\[(\d+)\]\{([^{}]+)\}""")) { "${it.groupValues[1]}√(${it.groupValues[2].trim()})" }

    // 5. Convert \text{...}, \mathrm{...}, \mathbf{...}, \textbf{...}
    text = text.replace(Regex("""\\(?:text|mathrm|mathbf|textbf|mathit)\{([^{}]+)\}""")) { it.groupValues[1] }

    // 6. Brackets & grouping
    text = text.replace("\\left(", "(").replace("\\right)", ")")
    text = text.replace("\\left[", "[").replace("\\right]", "]")
    text = text.replace("\\left\\{", "{").replace("\\right\\}", "}")
    text = text.replace("\\left|", "|").replace("\\right|", "|")

    // 7. Operators & symbols
    text = text.replace("\\times", "×")
    text = text.replace("\\cdot", "·")
    text = text.replace("\\div", "÷")
    text = text.replace("\\pm", "±")
    text = text.replace("\\mp", "∓")
    text = text.replace("\\leq", "≤").replace("\\le", "≤")
    text = text.replace("\\geq", "≥").replace("\\ge", "≥")
    text = text.replace("\\neq", "≠").replace("\\ne", "≠")
    text = text.replace("\\approx", "≈")
    text = text.replace("\\equiv", "≡")
    text = text.replace("\\infty", "∞")
    text = text.replace("\\pi", "π")
    text = text.replace("\\theta", "θ")
    text = text.replace("\\alpha", "α")
    text = text.replace("\\beta", "β")
    text = text.replace("\\gamma", "γ")
    text = text.replace("\\delta", "δ")
    text = text.replace("\\Delta", "Δ")
    text = text.replace("\\lambda", "λ")
    text = text.replace("\\degree", "°").replace("^{\\circ}", "°").replace("^\\circ", "°")

    // 8. Spacing
    text = text.replace("\\quad", "  ").replace("\\qquad", "   ")
    text = text.replace("\\,", " ").replace("\\;", " ").replace("\\!", "")

    // 9. Inline $ delimiters: strip single $ wrapping math terms
    text = text.replace(Regex("""\$([^\$\n]+)\$""")) { match ->
        match.groupValues[1]
    }

    return text
}

private fun parseMarkdown(raw: String): List<MarkdownElement> {
    val lines = raw.lines()
    val elements = mutableListOf<MarkdownElement>()
    var inCodeBlock = false
    var codeLang = ""
    val codeBuilder = StringBuilder()

    var i = 0
    while (i < lines.size) {
        val rawLine = lines[i]
        if (rawLine.trim().startsWith("```")) {
            if (inCodeBlock) {
                elements.add(MarkdownElement.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
                codeBuilder.clear()
                inCodeBlock = false
                codeLang = ""
            } else {
                inCodeBlock = true
                codeLang = rawLine.trim().removePrefix("```").trim()
            }
            i++
            continue
        }

        if (inCodeBlock) {
            codeBuilder.append(rawLine).append("\n")
            i++
            continue
        }

        val line = cleanMathAndLatex(rawLine)
        val trimmed = line.trim()
        when {
            trimmed.startsWith("### ") -> {
                elements.add(MarkdownElement.Header(3, trimmed.removePrefix("### ")))
            }
            trimmed.startsWith("## ") -> {
                elements.add(MarkdownElement.Header(2, trimmed.removePrefix("## ")))
            }
            trimmed.startsWith("# ") -> {
                elements.add(MarkdownElement.Header(1, trimmed.removePrefix("# ")))
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val bulletText = if (trimmed.startsWith("- ")) trimmed.removePrefix("- ") else trimmed.removePrefix("* ")
                elements.add(MarkdownElement.BulletItem(bulletText))
            }
            else -> {
                if (trimmed.isNotEmpty()) {
                    val imgRegex = Regex("""!\[(.*?)\]\((.*?)\)""")
                    val matches = imgRegex.findAll(line).toList()
                    if (matches.isNotEmpty()) {
                        var currentIndex = 0
                        for (match in matches) {
                            val textBefore = line.substring(currentIndex, match.range.first)
                            if (textBefore.trim().isNotEmpty()) {
                                elements.add(MarkdownElement.Paragraph(textBefore))
                            }
                            val altText = match.groupValues.getOrNull(1) ?: ""
                            val url = match.groupValues.getOrNull(2) ?: ""
                            elements.add(MarkdownElement.Image(altText, url))
                            currentIndex = match.range.last + 1
                        }
                        val textAfter = line.substring(currentIndex)
                        if (textAfter.trim().isNotEmpty()) {
                            elements.add(MarkdownElement.Paragraph(textAfter))
                        }
                    } else {
                        elements.add(MarkdownElement.Paragraph(line))
                    }
                }
            }
        }
        i++
    }

    if (inCodeBlock && codeBuilder.isNotEmpty()) {
        elements.add(MarkdownElement.CodeBlock(codeLang, codeBuilder.toString().trimEnd()))
    }

    return elements
}

/**
 * Builds annotated text supporting **bold**, *italic*, and `inline code`
 */
@Composable
fun buildStyledText(raw: String) = buildAnnotatedString {
    var index = 0
    while (index < raw.length) {
        val nextBold = raw.indexOf("**", index)
        val nextCode = raw.indexOf("`", index)

        // Check which delimiter comes first
        val firstDelimiterIndex = listOfNotNull(
            if (nextBold != -1) nextBold else null,
            if (nextCode != -1) nextCode else null
        ).minOrNull()

        if (firstDelimiterIndex == null) {
            append(raw.substring(index))
            break
        }

        // Append text before delimiter
        if (firstDelimiterIndex > index) {
            append(raw.substring(index, firstDelimiterIndex))
            index = firstDelimiterIndex
        }

        if (index == nextBold) {
            val endBold = raw.indexOf("**", index + 2)
            if (endBold != -1) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = SaifTheme.colors.textPrimary)) {
                    append(raw.substring(index + 2, endBold))
                }
                index = endBold + 2
            } else {
                append(raw.substring(index))
                break
            }
        } else if (index == nextCode) {
            val endCode = raw.indexOf("`", index + 1)
            if (endCode != -1) {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0xFF1E293B),
                        color = Color(0xFF38BDF8),
                        fontSize = 13.sp
                    )
                ) {
                    append(" ${raw.substring(index + 1, endCode)} ")
                }
                index = endCode + 1
            } else {
                append(raw.substring(index))
                break
            }
        }
    }
}
