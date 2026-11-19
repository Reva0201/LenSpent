package org.umn.lensspent

import android.net.Uri
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.umn.lensspent.data.model.Category
import org.umn.lensspent.ui.components.CategoryTilePicker
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing
import org.umn.lensspent.ui.theme.motionTween
import org.umn.lensspent.ui.theme.rememberAnimationsEnabled
private val PreviewHeight = 220.dp

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
                    IconButton(
                        onClick = {
                            // Backing out of an unfinished review is a discard, not a silent exit:
                            // otherwise the capture row and its private photo would linger with no
                            // way to reach them. Once the expense is saved the capture is already
                            // gone, so there is nothing left to clean up and the plain callback is
                            // the correct path.
                            val state = uiState
                            if (state is ExpenseReviewUiState.Editing ||
                                state is ExpenseReviewUiState.Processing
                            ) {
                                viewModel.discard()
                            } else {
                                onFinished()
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Discard and go back"
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
    Dialog(
        onDismissRequest = onDismiss,
        // The card sizes itself: the platform's default dialog width would leave a narrow column of
        // text in the middle of a wide phone.
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val cardScale = remember { Animatable(0.94f) }
        val contentAlpha = remember { Animatable(0f) }
        val tickProgress = remember { Animatable(0f) }
        val animationsEnabled = rememberAnimationsEnabled()

        LaunchedEffect(Unit) {
            if (!animationsEnabled) {
                cardScale.snapTo(1f)
                contentAlpha.snapTo(1f)
                tickProgress.snapTo(1f)
                return@LaunchedEffect
            }
            // The card grows into place on the standard curve, then the tick draws while the copy
            // fades up. No overshoot: this is a confirmation, and a card that bounces before settling
            // reads as decoration rather than as an answer.
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = motionTween(Motion.Standard, easing = Motion.Decelerate)
            )
            launch { contentAlpha.animateTo(1f, motionTween(Motion.Standard)) }
            tickProgress.animateTo(
                targetValue = 1f,
                animationSpec = motionTween(
                    durationMillis = Motion.Emphasized,
                    easing = Motion.Decelerate
                )
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.xl)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                },
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(Spacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(Spacing.sm)
            ) {
                SavedCheck(progress = { tickProgress.value })

                Spacer(modifier = Modifier.height(Spacing.sm))

                Column(
                    modifier = Modifier.alpha(contentAlpha.value),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        text = "Expense saved",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "${saved.merchant} - ${formatIDR(saved.amountRupiah)}",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = saved.categoryName?.let { "Added to $it spending." }
                            ?: "Saved without a category name.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = dayLabel(saved.date),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "It's in your recent transactions now.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.sm))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = "Done",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
        }
    }
}

/**
 * The tick, drawn rather than shown.
 *
 * A check mark that appears already finished is a picture of a tick; one that draws itself is the app
 * confirming something. Two layers: a translucent halo that gives the mark weight against the card, and
 * the tick stroked along a measured path so it grows from the first point to the last.
 */
@Composable
private fun SavedCheck(progress: () -> Float) {
    // Read outside the draw scope: a Canvas lambda is not composable, so the role has to be resolved
    // before the drawing starts.
    val tickColor = MaterialTheme.colorScheme.onPrimary

    Box(
        modifier = Modifier.size(76.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f))
        )
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val tick = Path().apply {
                    moveTo(size.width * 0.14f, size.height * 0.54f)
                    lineTo(size.width * 0.41f, size.height * 0.80f)
                    lineTo(size.width * 0.88f, size.height * 0.22f)
                }
                val measure = PathMeasure().apply { setPath(tick, false) }
                val drawn = Path()
                measure.getSegment(
                    startDistance = 0f,
                    stopDistance = measure.length * progress().coerceIn(0f, 1f),
                    destination = drawn,
                    startWithMoveTo = true
                )
                drawPath(
                    path = drawn,
                    // The tick sits on `primary`, so it is drawn in `onPrimary` rather than white: the
                    // two roles are the same in light mode and different in dark, which is exactly why
                    // the role exists.
                    color = tickColor,
                    style = Stroke(
                        width = 3.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }
        }
    }
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
                // True for the OCR pass itself: the photo and its text stay in app-private storage.
                // The optional AI feature is a separate, opt-in act on aggregate totals, so this
                // sentence does not claim the whole app never talks to a server.
                text = "The receipt is read on this device. The photo stays in app-private storage.",
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
        verticalArrangement = Arrangement.spacedBy(Spacing.xl)
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
            shape = MaterialTheme.shapes.small,
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
            shape = MaterialTheme.shapes.small,
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

        Spacer(modifier = Modifier.height(Spacing.xl))
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
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
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
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium
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
                // Fit rather than Crop: this image is evidence for the numbers below it. Cropping a
                // long receipt can hide exactly the total or merchant the user is checking.
                contentScale = ContentScale.Fit
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

/** The taxonomy as the same tile row the manual form uses for the same choice. */
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
        CategoryTilePicker(
            categories = categories,
            selectedId = selectedCategoryId,
            onSelect = onSelect
        )
    }
}
