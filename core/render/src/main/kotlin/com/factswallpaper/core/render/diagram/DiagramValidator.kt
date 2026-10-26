package com.factswallpaper.core.render.diagram

/**
 * Deterministic security sanitizer and structural validator from specification §7.4 and
 * the Tier 1 checks in F8. It runs before any markup reaches a WebView or is persisted.
 */
object DiagramValidator {

    private val hexEntity = Regex("&#x([0-9a-fA-F]+);")
    private val decimalEntity = Regex("&#([0-9]+);")
    private val namedEntity = Regex("&(colon|tab|newline);", RegexOption.IGNORE_CASE)
    private val whitespace = Regex("\\s+")
    private val eventHandler = Regex("(^|[\\s/])on[a-z]+\\s*=")
    private val environment = Regex("\\\\(begin|end)\\s*\\{([A-Za-z*]+)}")

    private val forbiddenFragments = listOf(
        "<script",
        "javascript:",
        "vbscript:",
        "data:text/html",
        "<iframe",
        "<object",
        "<embed",
    )

    private val latexForbiddenCommands = listOf(
        "\\input",
        "\\include",
        "\\write18",
        "\\openout",
        "\\csname",
    )

    sealed interface Result {
        data object Valid : Result
        data class Invalid(val reason: String) : Result
    }

    /**
     * Normalizes entities/control characters exactly once and validates the result for
     * [kind]. The normalized string is what should be passed on to the renderer.
     */
    fun validate(kind: MarkupKind, source: String): Result {
        val normalized = normalize(source)
        if (normalized.isBlank()) return Result.Invalid("Markup is empty")

        val lower = normalized.lowercase()
        forbiddenFragments.firstOrNull { lower.contains(it) }?.let {
            return Result.Invalid("Forbidden markup fragment: $it")
        }
        if (lower.contains("<link") && (lower.contains("http://") || lower.contains("https://"))) {
            return Result.Invalid("External stylesheet links are not allowed")
        }
        if (lower.contains("@import") && (lower.contains("http://") || lower.contains("https://"))) {
            return Result.Invalid("External CSS imports are not allowed")
        }
        if (eventHandler.containsMatchIn(lower)) {
            return Result.Invalid("Inline event handlers are not allowed")
        }

        return when (kind) {
            MarkupKind.SVG -> validateSvg(normalized)
            MarkupKind.LATEX -> validateLatex(normalized)
            MarkupKind.MERMAID,
            MarkupKind.TIKZ,
            MarkupKind.HTML_CANVAS,
            -> Result.Valid
        }
    }

    fun isValid(kind: MarkupKind, source: String): Boolean = validate(kind, source) is Result.Valid

    fun isSafeSvg(source: String): Boolean = isValid(MarkupKind.SVG, source)

    fun isSafeHtml(source: String): Boolean = isValid(MarkupKind.HTML_CANVAS, source)

    fun isSafeLatex(source: String): Boolean = isValid(MarkupKind.LATEX, source)

    /** Applies only the deterministic normalization pass, exposed for diagnostics/tests. */
    fun normalize(source: String): String {
        var value = source.replace("\u0000", "")
        value = hexEntity.replace(value) { match ->
            match.groupValues[1].toIntOrNull(16)
                ?.takeIf { it in 0..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        value = decimalEntity.replace(value) { match ->
            match.groupValues[1].toIntOrNull()
                ?.takeIf { it in 0..0x10FFFF }
                ?.let { String(Character.toChars(it)) }
                ?: match.value
        }
        value = namedEntity.replace(value) { match ->
            when (match.groupValues[1].lowercase()) {
                "colon" -> ":"
                "tab" -> "\t"
                "newline" -> "\n"
                else -> match.value
            }
        }
        return whitespace.replace(value, " ").trim()
    }

    private fun validateSvg(source: String): Result {
        val lower = source.lowercase()
        if (!lower.contains("<svg")) return Result.Invalid("SVG must contain an <svg> root")
        if (!lower.contains("</svg>")) return Result.Invalid("SVG must be closed with </svg>")
        return Result.Valid
    }

    private fun validateLatex(source: String): Result {
        val lower = source.lowercase()
        latexForbiddenCommands.firstOrNull { lower.contains(it) }?.let {
            return Result.Invalid("Forbidden LaTeX command: $it")
        }
        val unbalanced = unbalancedBrace(source)
        if (unbalanced != null) return Result.Invalid(unbalanced)
        if (!leftRightBalanced(source)) {
            return Result.Invalid("Unbalanced \\left and \\right delimiters")
        }
        if (!environmentsBalanced(source)) {
            return Result.Invalid("Unbalanced LaTeX environments")
        }
        return Result.Valid
    }

    /** Counts unescaped braces; `\\{` and `\\}` are literal glyphs, not grouping tokens. */
    private fun unbalancedBrace(source: String): String? {
        var depth = 0
        var index = 0
        while (index < source.length) {
            when (val char = source[index]) {
                '\\' -> index += 2 // skip the escaped character, including \{ and \}
                '{' -> {
                    depth++
                    index++
                }
                '}' -> {
                    depth--
                    if (depth < 0) return "Unbalanced closing brace"
                    index++
                }
                else -> {
                    if (char == '%') {
                        // Ignore the remainder of a LaTeX comment line.
                        index = source.indexOf('\n', index).takeIf { it >= 0 } ?: source.length
                    } else {
                        index++
                    }
                }
            }
        }
        return if (depth == 0) null else "Unbalanced opening brace"
    }

    private fun leftRightBalanced(source: String): Boolean {
        val leftCount = Regex("\\\\left(?![a-zA-Z])").findAll(source).count()
        val rightCount = Regex("\\\\right(?![a-zA-Z])").findAll(source).count()
        return leftCount == rightCount
    }

    private fun environmentsBalanced(source: String): Boolean {
        val stack = ArrayDeque<String>()
        environment.findAll(source).forEach { match ->
            val action = match.groupValues[1]
            val name = match.groupValues[2]
            if (action == "begin") {
                stack.addLast(name)
            } else {
                if (stack.isEmpty() || stack.last() != name) return false
                stack.removeLast()
            }
        }
        return stack.isEmpty()
    }
}
