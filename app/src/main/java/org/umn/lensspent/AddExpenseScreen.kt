package org.umn.lensspent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.ui.components.CategoryTilePicker
import org.umn.lensspent.ui.theme.Elevation
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing

/**
 * The Form screen: the one place in the app where a transaction starts from nothing.
 *
 * Three things are deliberate here, because they are the three things a form is judged on.
 *
 * Every field declares its keyboard. The amount is a number pad, the merchant capitalises words and
 * offers "next", and the note is free text. A field that leaves this to the default is a field the user
 * has to fight.
 *
 * Every field reports its own error, in place, using `isError` and `supportingText` rather than a dialog
 * at the end. The amount's helper line doubles as a live echo of what will be saved, so the user can see
 * "Rp 45.000" forming as they type instead of trusting a number they cannot re-read.
 *
 * Save is disabled until the form is valid, and a line under it says what is missing, so a blocked
 * button explains itself instead of just being dead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddExpenseScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddExpenseViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current

    LaunchedEffect(saved) {
        if (saved) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "New expense",
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
                }
            )
        },
        bottomBar = {
            // Save lives in a bar, not at the end of the scrolling form: with the keyboard open the
            // button would otherwise be several fields below the fold, and a form whose submit action
            // you have to hunt for is a form people abandon.
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = Elevation.floating
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        // The bar has to ride above the keyboard: a sticky action that the IME covers
                        // is no better than one at the end of the form. The navigation-bar inset is
                        // handled inside the same modifier chain, once, so the button never sits under
                        // either system surface.
                        .navigationBarsPadding()
                        .imePadding()
                        .padding(horizontal = Spacing.xl, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                ) {
                    if (!uiState.canSave && !uiState.isSaving) {
                        Text(
                            text = "Fill in the amount, merchant and category to save.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Button(
                        onClick = viewModel::save,
                        enabled = uiState.canSave,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        if (uiState.isSaving) {
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
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xl, vertical = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            AmountField(
                uiState = uiState,
                onAmountChange = viewModel::onAmountChange,
                onDone = { focusManager.clearFocus() }
            )

            MerchantField(
                uiState = uiState,
                onMerchantChange = viewModel::onMerchantChange
            )

            CategoryPicker(
                uiState = uiState,
                onCategorySelected = viewModel::onCategorySelected
            )

            DateStepper(
                uiState = uiState,
                onStep = { days -> viewModel.onDayStep(days.toLong()) }
            )

            NoteField(
                uiState = uiState,
                onNoteChange = viewModel::onNoteChange
            )

            Spacer(modifier = Modifier.height(Spacing.xxl))
        }
    }
}

/**
 * The amount: the largest field on the screen, a number pad, and a helper line that is either the live
 * echo of the parsed value or the reason it cannot be parsed.
 */
@Composable
private fun AmountField(
    uiState: AddExpenseUiState,
    onAmountChange: (String) -> Unit,
    onDone: () -> Unit
) {
    OutlinedTextField(
        value = uiState.amountText,
        onValueChange = onAmountChange,
        label = { Text("Amount") },
        prefix = {
            Text(
                text = "Rp",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        singleLine = true,
        isError = uiState.amountError,
        supportingText = {
            val parsed = uiState.amountRupiah
            Text(
                text = when {
                    uiState.amountError -> uiState.amountSupportingText
                    parsed != null && parsed > 0L -> "Saves as ${formatIDR(parsed)}"
                    else -> "Whole Rupiah, digits only"
                }
            )
        },
        textStyle = MaterialTheme.typography.headlineSmall,
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Number,
            imeAction = ImeAction.Done
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
private fun MerchantField(
    uiState: AddExpenseUiState,
    onMerchantChange: (String) -> Unit
) {
    OutlinedTextField(
        value = uiState.merchant,
        onValueChange = onMerchantChange,
        label = { Text("Merchant") },
        placeholder = { Text("Where was it spent?") },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Storefront,
                contentDescription = null,
                modifier = Modifier.size(Sizes.iconSmall)
            )
        },
        singleLine = true,
        isError = uiState.merchantError,
        supportingText = {
            Text(
                if (uiState.merchantError) {
                    "Merchant is required"
                } else {
                    "The shop or service on the receipt"
                }
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Words,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Next
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Categories as selectable chips rather than a spinner: the taxonomy is nine rows, and seeing all of it
 * at once is both faster and the only way the user can tell that choosing one is required.
 */
@Composable
private fun CategoryPicker(
    uiState: AddExpenseUiState,
    onCategorySelected: (Long) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Category",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (uiState.categoryError) {
                Text(
                    text = "Choose one",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        CategoryTilePicker(
            categories = uiState.categories,
            selectedId = uiState.selectedCategoryId,
            onSelect = onCategorySelected,
            modifier = Modifier.padding(top = Spacing.sm)
        )
    }
}

/** The same stepper the review sheet uses, so the two dates are read and stepped identically. */
@Composable
private fun DateStepper(
    uiState: AddExpenseUiState,
    onStep: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Text(
            text = "Date",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        StepperHeader(
            label = dayLabel(uiState.selectedDate),
            nextEnabled = uiState.canStepForward,
            onStep = onStep
        )
    }
}

@Composable
private fun NoteField(
    uiState: AddExpenseUiState,
    onNoteChange: (String) -> Unit
) {
    OutlinedTextField(
        value = uiState.note,
        onValueChange = onNoteChange,
        label = { Text("Note (optional)") },
        placeholder = { Text("What was it for?") },
        minLines = 3,
        isError = uiState.noteOverLimit,
        supportingText = {
            Text(
                text = "${uiState.note.length} / $NoteMaxLength",
                color = if (uiState.noteOverLimit) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        },
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.Sentences,
            keyboardType = KeyboardType.Text,
            imeAction = ImeAction.Default
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth()
    )
}
