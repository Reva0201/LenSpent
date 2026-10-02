package org.umn.lensspent.ui.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

private val BlockSpacing = 6.dp

/** Room for the bullet, so a wrapped list item lines up under its own text and not under the dot. */
private val BulletGutter = 16.dp

/**
 * Draws an assistant answer, translating [parseMarkdownLite]'s blocks into text.
 *
 * The parsing itself is elsewhere and pure; this only decides what bold and a bullet look like. The
 * result is a [Column], so however long the answer is it simply grows, and the chat's scrolling stays
 * the list's job.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface
) {
    // Answers are immutable once they land, so a re-render never re-parses one.
    val blocks = remember(text) { parseMarkdownLite(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(BlockSpacing)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Paragraph -> Text(
                    text = block.spans.toAnnotatedString(),
                    style = style,
                    color = color
                )

                is MarkdownBlock.Bullet -> Row {
                    Text(
                        text = "•",
                        style = style,
                        color = color,
                        modifier = Modifier.width(BulletGutter)
                    )
                    Text(
                        text = block.spans.toAnnotatedString(),
                        style = style,
                        color = color
                    )
                }
            }
        }
    }
}

/** Bold is the only emphasis an answer uses, so translating a span is one branch. */
private fun List<MarkdownSpan>.toAnnotatedString(): AnnotatedString = buildAnnotatedString {
    this@toAnnotatedString.forEach { span ->
        when (span) {
            is MarkdownSpan.Plain -> append(span.text)
            is MarkdownSpan.Bold ->
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(span.text) }
        }
    }
}
