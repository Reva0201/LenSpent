package org.umn.lensspent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.ui.markdown.MarkdownText

/** Screen-level layout tokens, grouped the way the analytics screen groups its own. */
private object AiChatDimens {
    val Gutter = 16.dp
    val BubblePadding = 12.dp
    val BubbleSpacing = 12.dp
    val BubbleMaxWidth = 320.dp
    val TailCorner = 4.dp
    val RoundCorner = 20.dp
    val InputSpacing = 8.dp
    val SendButtonSize = 56.dp
    val IndicatorSize = 18.dp
    val IndicatorStroke = 2.dp
    val DisclaimerSpacing = 8.dp
    val DisclaimerBottomSpacing = 12.dp
}

/** The bubble corner nearest the speaker is squared off, so a turn reads as coming from one side. */
private val UserBubbleShape = RoundedCornerShape(
    topStart = AiChatDimens.RoundCorner,
    topEnd = AiChatDimens.RoundCorner,
    bottomEnd = AiChatDimens.TailCorner,
    bottomStart = AiChatDimens.RoundCorner
)

private val AssistantBubbleShape = RoundedCornerShape(
    topStart = AiChatDimens.TailCorner,
    topEnd = AiChatDimens.RoundCorner,
    bottomEnd = AiChatDimens.RoundCorner,
    bottomStart = AiChatDimens.RoundCorner
)

/**
 * Premium AI Insights: a chat over the same ledger the dashboard shows.
 *
 * The screen is deliberately plain - a transcript, an input and the disclaimer - because everything
 * interesting happens one layer down in [AiChatViewModel] and the repository behind it. Answers are
 * drawn by [MarkdownText], so bold and bullets come out as bold and bullets.
 *
 * The disclaimer under the input is neither dismissible nor conditional: it is the last thing on the
 * screen in every state, including before the first question is asked.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    onNavigateBack: () -> Unit,
    viewModel: AiChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // The newest bubble is the one being read, so each turn scrolls itself into view - including the
    // placeholder that stands in for an answer still on its way.
    LaunchedEffect(uiState.messages.size, uiState.isAwaitingReply) {
        val newestIndex = uiState.messages.size - 1 + if (uiState.isAwaitingReply) 1 else 0
        if (newestIndex >= 0) listState.animateScrollToItem(newestIndex)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "AI Insights",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Premium • Powered by DeepSeek",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to the dashboard"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(AiChatDimens.Gutter),
                verticalArrangement = Arrangement.spacedBy(AiChatDimens.BubbleSpacing)
            ) {
                items(uiState.messages) { message -> AiChatBubble(message) }

                if (uiState.isAwaitingReply) {
                    item { AwaitingReplyBubble() }
                }
            }

            // Lifted with the keyboard, and always below the transcript: the input, then the
            // disclaimer the feature has to state.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding()
            ) {
                AiChatInputRow(
                    draft = uiState.draft,
                    canSend = uiState.canSend,
                    onDraftChange = viewModel::onDraftChange,
                    onSend = viewModel::send
                )
                AiDisclaimerNote()
            }
        }
    }
}

/**
 * One turn of the conversation.
 *
 * A question is drawn exactly as typed - markdown in it is the user's own text, not a format - while an
 * answer goes through [MarkdownText]. A failure is drawn in the error container, so it reads as a
 * message from the assistant rather than as the app breaking.
 */
@Composable
private fun AiChatBubble(message: AiChatMessage) {
    val fromUser = message.author == AiChatAuthor.User

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (fromUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            shape = if (fromUser) UserBubbleShape else AssistantBubbleShape,
            color = when {
                fromUser -> MaterialTheme.colorScheme.primary
                message.isError -> MaterialTheme.colorScheme.errorContainer
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = AiChatDimens.BubbleMaxWidth)
                    .padding(AiChatDimens.BubblePadding)
            ) {
                if (fromUser) {
                    Text(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    MarkdownText(
                        text = message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (message.isError) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

/** The loading indicator: the assistant's bubble, holding a spinner while the answer is fetched. */
@Composable
private fun AwaitingReplyBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Surface(shape = AssistantBubbleShape, color = MaterialTheme.colorScheme.surfaceVariant) {
            Row(
                modifier = Modifier.padding(AiChatDimens.BubblePadding),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AiChatDimens.InputSpacing)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(AiChatDimens.IndicatorSize),
                    strokeWidth = AiChatDimens.IndicatorStroke,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Reading your recent spending…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** The question field and its send button, which goes quiet while an answer is on its way. */
@Composable
private fun AiChatInputRow(
    draft: String,
    canSend: Boolean,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiChatDimens.Gutter),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(AiChatDimens.InputSpacing)
    ) {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(text = "Ask about your spending…") },
            shape = MaterialTheme.shapes.large,
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() })
        )
        FilledIconButton(
            onClick = onSend,
            enabled = canSend,
            modifier = Modifier.size(AiChatDimens.SendButtonSize)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send question"
            )
        }
    }
}

/**
 * The static disclaimer: the last thing on the screen, under the input, always.
 *
 * [AiInsightsDisclaimer] rather than a literal, so the wording is asserted by a test rather than
 * retyped here.
 */
@Composable
private fun AiDisclaimerNote() {
    Text(
        text = AiInsightsDisclaimer,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AiChatDimens.Gutter)
            .padding(
                top = AiChatDimens.DisclaimerSpacing,
                bottom = AiChatDimens.DisclaimerBottomSpacing
            )
    )
}

