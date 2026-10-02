package org.umn.lensspent.ui.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The rendering rules, pinned.
 *
 * DeepSeek answers in Markdown, and the chat renders exactly two of its constructions: bold and bullet
 * points. Everything a model actually emits around those two - headings, numbered lists, sentences
 * wrapped across lines, a stray unmatched `**` - is a case here rather than something to discover on a
 * phone.
 */
class MarkdownLiteTest {

    @Test
    fun `plain text is one paragraph`() {
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("You spent Rp 975.000.")))),
            parseMarkdownLite("You spent Rp 975.000.")
        )
    }

    @Test
    fun `bold is a span, not a marker`() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(
                    listOf(
                        MarkdownSpan.Plain("Total: "),
                        MarkdownSpan.Bold("Rp 975.000"),
                        MarkdownSpan.Plain(" this month.")
                    )
                )
            ),
            parseMarkdownLite("Total: **Rp 975.000** this month.")
        )
    }

    @Test
    fun `bullet lines become bullets and keep their bold`() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("Where it went:"))),
                MarkdownBlock.Bullet(
                    listOf(MarkdownSpan.Bold("Groceries"), MarkdownSpan.Plain(" - Rp 850.000"))
                ),
                MarkdownBlock.Bullet(listOf(MarkdownSpan.Plain("Dining - Rp 125.000")))
            ),
            parseMarkdownLite("Where it went:\n- **Groceries** - Rp 850.000\n* Dining - Rp 125.000")
        )
    }

    @Test
    fun `numbered lists and heading markers are handled too`() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownSpan.Bold("Summary"))),
                MarkdownBlock.Bullet(listOf(MarkdownSpan.Plain("Groceries first"))),
                MarkdownBlock.Bullet(listOf(MarkdownSpan.Plain("Dining second")))
            ),
            parseMarkdownLite("## Summary\n1. Groceries first\n2) Dining second")
        )
    }

    @Test
    fun `a single newline is a space and a blank line is a break`() {
        assertEquals(
            listOf(
                MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("One sentence, wrapped by the model."))),
                MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("A second thought.")))
            ),
            parseMarkdownLite("One sentence,\nwrapped by the model.\n\nA second thought.")
        )
    }

    /**
     * Half an answer should read as half an answer: an unmatched marker is left as the characters the
     * model typed rather than bolding everything after it.
     */
    @Test
    fun `an unpaired marker stays literal`() {
        val expected = listOf(MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("**Groceries are up"))))

        assertEquals(expected, parseMarkdownLite("**Groceries are up"))
        assertEquals(
            listOf(MarkdownBlock.Paragraph(listOf(MarkdownSpan.Plain("****")))),
            parseMarkdownLite("****")
        )
    }

    @Test
    fun `an empty answer is no blocks at all`() {
        assertEquals(emptyList<MarkdownBlock>(), parseMarkdownLite(""))
        assertEquals(emptyList<MarkdownBlock>(), parseMarkdownLite("\n   \n"))
    }
}
