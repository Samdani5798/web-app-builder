package com.example.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import java.util.regex.Pattern

class CodeSyntaxHighlighter(private val fileExtension: String) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.text.length > 15000) {
            // Bypass heavy regex highlighter for large files (>15kb) to prevent UI thread blocking, lag, and crashes
            return TransformedText(text, OffsetMapping.Identity)
        }
        val highlighted = when (fileExtension.lowercase()) {
            "html", "xml" -> highlightHtml(text.text)
            "css" -> highlightCss(text.text)
            "js", "javascript" -> highlightJs(text.text)
            "json" -> highlightJson(text.text)
            else -> AnnotatedString(text.text) // Plain text
        }
        return TransformedText(highlighted, OffsetMapping.Identity)
    }

    private fun highlightHtml(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)
        
        // Match HTML tags (e.g. <div, </div, key=)
        val tagPattern = Pattern.compile("(<\\/?[a-zA-Z0-9:]+\\s*|\\s*\\/?>)")
        val tagMatcher = tagPattern.matcher(code)
        while (tagMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFE5C07B), fontWeight = FontWeight.Bold),
                tagMatcher.start(),
                tagMatcher.end()
            )
        }

        // Match Attributes
        val attrPattern = Pattern.compile("\\b([a-zA-Z0-9\\-]+)\\s*=")
        val attrMatcher = attrPattern.matcher(code)
        while (attrMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF61AFEF)),
                attrMatcher.start(1),
                attrMatcher.end(1)
            )
        }

        // Match Quotes/Strings
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'")
        val stringMatcher = stringPattern.matcher(code)
        while (stringMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF98C379)),
                stringMatcher.start(),
                stringMatcher.end()
            )
        }

        // Match Comments<!-- ... -->
        val commentPattern = Pattern.compile("<!--[\\s\\S]*?-->")
        val commentMatcher = commentPattern.matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF5C6370)),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        return builder.toAnnotatedString()
    }

    private fun highlightCss(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)

        // Selectors and braces
        val bracePattern = Pattern.compile("[{}();:]")
        val braceMatcher = bracePattern.matcher(code)
        while (braceMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFABB2BF), fontWeight = FontWeight.Bold),
                braceMatcher.start(),
                braceMatcher.end()
            )
        }

        // CSS keywords and rule Properties
        val propPattern = Pattern.compile("(?m)^\\s*([a-zA-Z\\-]+)\\s*:")
        val propMatcher = propPattern.matcher(code)
        while (propMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFE06C75)),
                propMatcher.start(1),
                propMatcher.end(1)
            )
        }

        // Values
        val valPattern = Pattern.compile(":\\s*([^;]+);")
        val valMatcher = valPattern.matcher(code)
        while (valMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF98C379)),
                valMatcher.start(1),
                valMatcher.end(1)
            )
        }

        // Numbers/Dimensions
        val numberPattern = Pattern.compile("\\b(\\d+)(px|em|rem|%|vh|vw|s|ms)?\\b")
        val numberMatcher = numberPattern.matcher(code)
        while (numberMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFD19A66)),
                numberMatcher.start(),
                numberMatcher.end()
            )
        }

        // Comments /* ... */
        val commentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
        val commentMatcher = commentPattern.matcher(code)
        while (commentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF5C6370)),
                commentMatcher.start(),
                commentMatcher.end()
            )
        }

        return builder.toAnnotatedString()
    }

    private fun highlightJs(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)

        // Keywords
        val keywords = listOf(
            "const", "let", "var", "function", "return", "if", "else", "for", "while",
            "do", "switch", "case", "break", "continue", "new", "this", "class", "extends",
            "import", "export", "default", "from", "async", "await", "true", "false", "null", "undefined"
        )
        val keywordPattern = Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")
        val keywordMatcher = keywordPattern.matcher(code)
        while (keywordMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFC678DD), fontWeight = FontWeight.Bold),
                keywordMatcher.start(),
                keywordMatcher.end()
            )
        }

        // Function calls
        val funcPattern = Pattern.compile("\\b([a-zA-Z0-9_]+)\\s*\\(")
        val funcMatcher = funcPattern.matcher(code)
        while (funcMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF61AFEF)),
                funcMatcher.start(1),
                funcMatcher.end(1)
            )
        }

        // Strings
        val stringPattern = Pattern.compile("\"[^\"]*\"|'[^']*'|`[^`]*`")
        val stringMatcher = stringPattern.matcher(code)
        while (stringMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF98C379)),
                stringMatcher.start(),
                stringMatcher.end()
            )
        }

        // Single line comments
        val slCommentPattern = Pattern.compile("//.*")
        val slCommentMatcher = slCommentPattern.matcher(code)
        while (slCommentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF5C6370)),
                slCommentMatcher.start(),
                slCommentMatcher.end()
            )
        }

        // Multi line comments
        val mlCommentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
        val mlCommentMatcher = mlCommentPattern.matcher(code)
        while (mlCommentMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF5C6370)),
                mlCommentMatcher.start(),
                mlCommentMatcher.end()
            )
        }

        return builder.toAnnotatedString()
    }

    private fun highlightJson(code: String): AnnotatedString {
        val builder = AnnotatedString.Builder(code)

        // Bracket colors
        val bracePattern = Pattern.compile("[\\[\\]{}:,]")
        val braceMatcher = bracePattern.matcher(code)
        while (braceMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFABB2BF), fontWeight = FontWeight.Bold),
                braceMatcher.start(),
                braceMatcher.end()
            )
        }

        // Keys on quotes
        val keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:")
        val keyMatcher = keyPattern.matcher(code)
        while (keyMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFE06C75), fontWeight = FontWeight.SemiBold),
                keyMatcher.start(1),
                keyMatcher.end(1)
            )
        }

        // Literal Values after colon
        val valPattern = Pattern.compile(":\\s*(true|false|null|\\d+(\\.\\d+)?)\\b")
        val valMatcher = valPattern.matcher(code)
        while (valMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFFD19A66)),
                valMatcher.start(1),
                valMatcher.end(1)
            )
        }

        // String Values after colon (double quoted strings)
        val strValPattern = Pattern.compile(":\\s*(\"[^\"]*\")")
        val strValMatcher = strValPattern.matcher(code)
        while (strValMatcher.find()) {
            builder.addStyle(
                SpanStyle(color = Color(0xFF98C379)),
                strValMatcher.start(1),
                strValMatcher.end(1)
            )
        }

        return builder.toAnnotatedString()
    }
}
