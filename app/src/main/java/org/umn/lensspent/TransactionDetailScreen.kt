package org.umn.lensspent

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.data.model.Transaction
import org.umn.lensspent.ui.theme.Elevation
import org.umn.lensspent.ui.theme.LocalHeroColors
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing

/**
 * One expense, in full: the amount, what it was for, when, which category, and where it came from.
 *
 * This is the app's Detail screen, and it exists to be reached *from* the list: the row id arrives as a
 * navigation argument and is what the screen is about, so nothing here is hardcoded and nothing is
 * passed through a shared mutable global.
 *
 * [onNavigateToHome] is separate from [onBack] because the missing state has nowhere to pop to: an
 * expense reached from a stale argument may be the only entry on the stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onNavigateToHome: () -> Unit,
    viewModel: TransactionDetailViewModel = viewModel(
        key = "transaction-detail-$transactionId",
        factory = AppViewModelProvider.transactionDetailFactory(transactionId)
    )
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmingDelete by viewModel.confirmingDelete.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()

    // Deleted: the row is gone, so this screen has nothing left to show. Popping returns the user to
    // the list they came from, which is the only place the deletion can be seen to have happened.
    LaunchedEffect(finished) {
        if (finished) onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Expense",
                        style = MaterialTheme.typography.titleLarge
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (uiState is TransactionDetailUiState.Found) {
                        IconButton(onClick = onEdit) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit this expense",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        IconButton(onClick = viewModel::requestDelete) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete this expense",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when (val state = uiState) {
            TransactionDetailUiState.Loading -> LoadingPane(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            TransactionDetailUiState.Missing -> MissingPane(
                onNavigateToHome = onNavigateToHome,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )

            is TransactionDetailUiState.Found -> FoundPane(
                transaction = state.transaction,
                onDelete = viewModel::requestDelete,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            )
        }
    }

    val transaction = (uiState as? TransactionDetailUiState.Found)?.transaction
    if (confirmingDelete && transaction != null) {
        DeleteConfirmationDialog(
            transaction = transaction,
            onConfirm = viewModel::confirmDelete,
            onDismiss = viewModel::cancelDelete
        )
    }
}

@Composable
private fun FoundPane(
    transaction: Transaction,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
    ) {
        HeroCard(transaction)

        DetailCard(transaction)

        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = MaterialTheme.shapes.medium
        ) {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(Sizes.iconSmall)
            )
            Spacer(modifier = Modifier.size(Spacing.sm))
            Text(
                text = "Delete expense",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.error
            )
        }

        Text(
            text = "Stored on this device only. Nothing about this expense is sent anywhere.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

/**
 * The amount as the one big thing on the page.
 *
 * A detail screen for money answers one question first - how much was it - so the figure is at display
 * size on the brand gradient, and everything else is subordinate. The gradient is the same pair the
 * dashboard's hero uses, which is what makes this read as "the same expense, opened" rather than as an
 * unrelated page.
 */
@Composable
private fun HeroCard(transaction: Transaction) {
    val hero = LocalHeroColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraLarge)
            .background(Brush.linearGradient(listOf(hero.start, hero.end)))
            .padding(horizontal = Spacing.xxl, vertical = Spacing.xxl)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(hero.content.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIcon(transaction.category.iconKey),
                    contentDescription = null,
                    tint = hero.content,
                    modifier = Modifier.size(Sizes.iconLarge)
                )
            }

            Spacer(modifier = Modifier.height(Spacing.sm))

            Text(
                text = transaction.merchant,
                style = MaterialTheme.typography.headlineSmall,
                color = hero.content,
                textAlign = TextAlign.Center
            )

            Text(
                text = formatIDR(transaction.amount),
                style = MaterialTheme.typography.displaySmall,
                color = hero.content,
                textAlign = TextAlign.Center
            )

            Surface(
                shape = MaterialTheme.shapes.small,
                color = hero.content.copy(alpha = 0.18f)
            ) {
                Text(
                    text = transaction.category.name.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = hero.content,
                    modifier = Modifier.padding(
                        horizontal = Spacing.md,
                        vertical = Spacing.xs
                    )
                )
            }

            Spacer(modifier = Modifier.height(Spacing.xs))

            Text(
                text = formatTransactionDate(transaction.occurredAt),
                style = MaterialTheme.typography.bodyMedium,
                color = hero.contentMuted
            )
        }
    }
}

/** The facts a receipt would carry, laid out as label/value rows rather than as a paragraph. */
@Composable
private fun DetailCard(transaction: Transaction) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm)) {
            DetailRow(label = "Category", value = transaction.category.name)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(label = "Date", value = formatTransactionDate(transaction.occurredAt))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(
                label = "Note",
                // An empty note is information, not an omission: saying so is better than a blank
                // half of a row that the user has to interpret.
                value = transaction.note?.takeIf { it.isNotBlank() } ?: "No note"
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            DetailRow(
                label = "Added",
                value = if (transaction.captureId != null) {
                    "From a receipt photo"
                } else {
                    "By hand"
                }
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LoadingPane(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** The row this screen was opened for is no longer in the ledger. */
@Composable
private fun MissingPane(
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(Spacing.xxxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.EditNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Sizes.iconLarge)
        )
        Spacer(modifier = Modifier.height(Spacing.lg))
        Text(
            text = "This expense is no longer in your ledger",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.sm))
        Text(
            text = "It may have been deleted from another screen.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(Spacing.xxl))
        OutlinedButton(
            onClick = onNavigateToHome,
            shape = MaterialTheme.shapes.medium
        ) {
            Text("Back to dashboard", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    transaction: Transaction,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Delete this expense?") },
        text = {
            Text(
                text = "${transaction.merchant} · ${formatIDR(transaction.amount)} will be " +
                    "removed from your ledger. This cannot be undone."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Keep")
            }
        },
        shape = MaterialTheme.shapes.large
    )
}
