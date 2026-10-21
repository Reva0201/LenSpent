package org.umn.lensspent

import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.ui.theme.Elevation
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing

private val PreviewHeight = 200.dp

/**
 * The confirmation step, and the only place an expense ever enters the ledger.
 *
 * Whatever extraction produced is shown for the user to correct - nothing is written until they press
 * save, which is what keeps a wrong OCR reading from becoming a wrong bank balance. Saving then shows
 * what was written and waits: the sheet leaves for the dashboard only once that has been read.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseReviewScreen(
    captureId: Long,
    onFinished: () -> Unit,
    viewModel: ExpenseReviewViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    val savedExpense by viewModel.savedExpense.collectAsStateWithLifecycle()

    LaunchedEffect(captureId) { viewModel.start(captureId) }
    LaunchedEffect(finished) {
        if (finished) {
            viewModel.consumeFinished()
            onFinished()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(text = "Confirm expense", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onFinished) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                // Edge-to-edge is enabled, so the app bar keeps its status bar inset
                windowInsets = TopAppBarDefaults.windowInsets
            )
        },
        bottomBar = {
            // The actions belong to the editable state only: there is nothing to save while the
            // receipt is still being read, and nothing to discard once it has been.
            val state = uiState
            if (state is ExpenseReviewUiState.Editing) {
                ReviewActionBar(state = state, viewModel = viewModel)
            }
        }
    ) { paddingValues ->
        when (val state = uiState) {
            null, is ExpenseReviewUiState.Processing -> ProcessingPane(
                modifier = Modifier.padding(paddingValues)
            )

            is ExpenseReviewUiState.Unavailable -> UnavailablePane(
                modifier = Modifier.padding(paddingValues),
                onDismiss = onFinished
            )

            is ExpenseReviewUiState.Editing -> EditingPane(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.padding(paddingValues)
            )
        }
    }

    // Read, then gone. The confirmation replaces navigating away rather than following it: the
    // dashboard only appears once this has been dismissed - by its button, by Back, or by a tap
    // outside, all of which acknowledgeSaved() treats as the same answer.
    savedExpense?.let { saved ->
        SaveConfirmationDialog(saved = saved, onDismiss = viewModel::acknowledgeSaved)
    }
}

/**
 * What the sheet says once the expense is in the ledger, before the dashboard appears.
 *
 * A saved expense that instantly becomes a dashboard is indistinguishable from one that never saved,
 * so the values the row was written from are read back to the user first: the merchant and the amount
 * as stored, the category it was filed under, and the day it landed on in [dayLabel]'s wording - the
 * same label the stepper above used, so the two cannot describe one date two different ways.
 *
 * `internal` rather than private so an instrumented test can render it on a real device and read it
 * the way a user would, with the row already written and nothing left to do but acknowledge it.
 */
@Composable
internal fun SaveConfirmationDialog(saved: SavedExpense, onDismiss: () -> Unit) {
    AlertDialog(
        // Back, or a tap outside, has read the confirmation just as surely as the button has
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = { Text(text = "Expense saved", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${saved.merchant} - ${formatIDR(saved.amountRupiah)}",
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = saved.categoryName?.let { "Added to $it spending." }
                        ?: "Saved without a category name."
                )
                Text(text = dayLabel(saved.date))
                Text(
                    text = "It's in your recent transactions now.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text(text = "Done") }
        }
    )
}

/**
 * First stage: the brief on-device work that happens before anything is editable.
 *
 * It disappears the moment there is something to edit, so the machinery that read the photo is never
 * the last thing on screen - what the photo became is.
 */
@Composable
private fun ProcessingPane(modifier: Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Reading your receipt",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Runs on this device - nothing is uploaded.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Shown when the capture behind a navigation argument no longer exists. */
@Composable
private fun UnavailablePane(modifier: Modifier, onDismiss: () -> Unit) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "This capture is no longer available.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text(text = "Go back") }
        }
    }
}

/**
 * The editable draft.
 *
 * The order is the order it is read in: what was photographed, then the number, then who and which
 * category, then when. The amount is the first field and the biggest, because it is the one value a
 * receipt is opened to check. Save stays disabled until there is a merchant, a positive amount and a
 * category, so the ledger can only ever receive complete rows.
 */
@Composable
private fun EditingPane(
    state: ExpenseReviewUiState.Editing,
    viewModel: ExpenseReviewViewModel,
    modifier: Modifier
) {
    val context = LocalContext.current
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val previewTargetPx = with(LocalDensity.current) { (PreviewHeight * 2).roundToPx() }

    LaunchedEffect(state.imageUri) {
        preview = withContext(Dispatchers.IO) {
            decodeSampledBitmap(context, Uri.parse(state.imageUri), previewTargetPx)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
    ) {
        CapturePreview(preview = preview)

        OutlinedTextField(
            value = state.amountText,
            onValueChange = viewModel::onAmountChange,
            label = { Text(text = "Total") },
            prefix = {
                Text(
                    text = "Rp",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            singleLine = true,
            isError = state.amountError,
            supportingText = {
                val amount = state.amountRupiah
                Text(
                    text = when {
                        state.amountError -> "Enter the total printed on the receipt"
                        amount != null && amount > 0L -> "Saves as ${formatIDR(amount)}"
                        else -> "Whole Rupiah, digits only"
                    }
                )
            },
            textStyle = MaterialTheme.typography.headlineSmall,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = state.merchant,
            onValueChange = viewModel::onMerchantChange,
            label = { Text(text = "Merchant") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Storefront,
                    contentDescription = null,
                    modifier = Modifier.size(Sizes.iconSmall)
                )
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                imeAction = ImeAction.Next
            ),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )

        CategoryPicker(
            categories = state.categories,
            selectedCategoryId = state.selectedCategoryId,
            onSelect = viewModel::onCategorySelected
        )

        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = "Date",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            // The shared stepper, so this screen and analytics step dates identically
            StepperHeader(
                label = dayLabel(state.selectedDate),
                // Same rule as analytics: an expense can be dated in the past, never in the future
                nextEnabled = state.canStepForward,
                onStep = { viewModel.onDayStep(it.toLong()) }
            )
        }

        Spacer(modifier = Modifier.height(Spacing.xxl))
    }
}

/**
 * Save and Discard, always reachable.
 *
 * The pair used to be two elements at the end of the scrolling panel: a full-width button and a text
 * button under it. On a phone that pushed Discard off screen and left Save under the keyboard. One bar
 * with the destructive-ish choice demoted to text and the primary action on the right keeps both
 * visible and makes the safe choice the obvious one.
 */
@Composable
private fun ReviewActionBar(
    state: ExpenseReviewUiState.Editing,
    viewModel: ExpenseReviewViewModel
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = Elevation.floating
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = Spacing.xl, vertical = Spacing.md),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = viewModel::discard,
                enabled = !state.isSaving
            ) {
                Text(
                    text = "Discard",
                    style = MaterialTheme.typography.labelLarge
                )
            }

            Button(
                onClick = viewModel::save,
                enabled = state.canSave,
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp),
                shape = MaterialTheme.shapes.large
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Sizes.iconSmall),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null,
                        modifier = Modifier.size(Sizes.iconSmall)
                    )
                    Spacer(modifier = Modifier.size(Spacing.sm))
                    Text(
                        text = "Save expense",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/** The captured image, decoded downsampled; a receipt icon stands in while it loads. */
@Composable
private fun CapturePreview(preview: ImageBitmap?) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(PreviewHeight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        if (preview != null) {
            Image(
                bitmap = preview,
                contentDescription = "Captured receipt",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** The taxonomy as wrapping chips, the same control the manual form uses for the same choice. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryPicker(
    categories: List<Category>,
    selectedCategoryId: Long?,
    onSelect: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            text = "Category",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            categories.forEach { category ->
                FilterChip(
                    selected = category.id == selectedCategoryId,
                    onClick = { onSelect(category.id) },
                    label = {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = categoryIcon(category.iconKey),
                            contentDescription = null,
                            modifier = Modifier.size(Sizes.iconSmall)
                        )
                    },
                    shape = MaterialTheme.shapes.small,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}
