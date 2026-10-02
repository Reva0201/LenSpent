package org.umn.lensspent.ui.markdown

/** A run of text with the emphasis DeepSeek asked for. */
sealed interface MarkdownSpan {

    val text: String

    data class Plain(override val text: String) : MarkdownSpan

    data class Bold(override val text: String) : MarkdownSpan
}

/** One line of an answer, as the chat card renders it. */
sealed interface MarkdownBlock {

    val spans: List<MarkdownSpan>

    /** Ordinary prose. */
    data class Paragraph(override val spans: List<MarkdownSpan>) : MarkdownBlock

    /** A list entry, drawn with a bullet in the gutter. */
    data class Bullet(override val spans: List<MarkdownSpan>) : MarkdownBlock
}

private const val BoldMarker = "**"

/** `- item`, `* item`, `• item` and friends - the shapes a model actually emits for a list. */
private val BulletMarkers = listOf("- ", "* ", "• ", "– ", "— ")

/** `1. item` and `1) item`. */
private val NumberedBullet = Regex("""^\d{1,2}[.)]\s+""")

/** `#`, `##`, ...: the chat shows one size of heading, so the hashes are stripped and the line bolded. */
private val Heading = Regex("""^#{1,6}\s+""")

/**
 * The subset of Markdown the AI Insights chat renders: `**bold**` and bullet points.
 *
 * A full Markdown engine would be a library, a renderer and a parsing pass per frame for an answer that
 * only ever uses bold and bullets. This is the whole feature: blank lines separate paragraphs, a single
 * newline is a space (the model wraps sentences), list lines become bullets, and `#`-prefixed lines
 * become bold headings.
 *
 * It is a pure function on purpose - no Compose, no Android - so every one of those rules is pinned by
 * a JVM test rather than by looking at the screen.
 */
fun parseMarkdownLite(answer: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()

    fun flushParagraph() {
        val text = paragraph.toString().trim()
        paragraph.clear()
        if (text.isNotEmpty()) blocks += MarkdownBlock.Paragraph(parseSpans(text))
    }

    answer.lines().forEach { rawLine ->
        val line = rawLine.trim()
        val bullet = bulletBody(line)
        val heading = headingBody(line)

        when {
            line.isEmpty() -> flushParagraph()

            bullet != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Bullet(parseSpans(bullet))
            }

            heading != null -> {
                flushParagraph()
                blocks += MarkdownBlock.Paragraph(headingSpans(heading))
            }

            else -> {
                if (paragraph.isNotEmpty()) paragraph.append(' ')
                paragraph.append(line)
            }
        }
    }
    flushParagraph()
    return blocks
}

/** The text after a list marker, or null when the line is not a list entry. */
private fun bulletBody(line: String): String? {
    BulletMarkers.firstOrNull { line.startsWith(it) }?.let { marker ->
        return line.removePrefix(marker).trim()
    }
    return NumberedBullet.find(line)?.let { match -> line.removePrefix(match.value).trim() }
}

/** The text after a heading marker, or null when the line is not a heading. */
private fun headingBody(line: String): String? =
    Heading.find(line)?.let { match -> line.removePrefix(match.value).trim() }

/** A heading is a bold line; the hashes themselves are not shown. */
private fun headingSpans(body: String): List<MarkdownSpan> {
    val spans = parseSpans(body)
    val only = spans.singleOrNull()
    return if (only is MarkdownSpan.Plain) listOf(MarkdownSpan.Bold(only.text)) else spans
}

/**
 * Splits a line into plain and bold runs.
 *
 * An unpaired `**` is left as literal text rather than swallowing the rest of the line: a truncated
 * answer should read as a truncated answer, not as emphasis the model never asked for.
 */
private fun parseSpans(text: String): List<MarkdownSpan> {
    val spans = mutableListOf<MarkdownSpan>()
    var index = 0

    while (index < text.length) {
        val open = text.indexOf(BoldMarker, index)
        val close = if (open < 0) -1 else text.indexOf(BoldMarker, open + BoldMarker.length)

        if (open < 0 || close < 0) {
            spans.appendPlain(text.substring(index))
            break
        }

        spans.appendPlain(text.substring(index, open))
        val body = text.substring(open + BoldMarker.length, close).trim()
        if (body.isEmpty()) spans.appendPlain(BoldMarker + BoldMarker) else spans += MarkdownSpan.Bold(body)
        index = close + BoldMarker.length
    }

    return spans
}

/** Keeps the output free of adjacent plain runs, so the renderer appends once per unbroken stretch. */
private fun MutableList<MarkdownSpan>.appendPlain(text: String) {
    if (text.isEmpty()) return
    val last = lastOrNull()
    if (last is MarkdownSpan.Plain) {
        this[lastIndex] = MarkdownSpan.Plain(last.text + text)
    } else {
        add(MarkdownSpan.Plain(text))
    }
}
