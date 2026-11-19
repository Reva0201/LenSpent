package org.umn.lensspent.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.AppViewModelProvider
import org.umn.lensspent.ui.budgetSupportingText
import org.umn.lensspent.ui.components.LensCard
import org.umn.lensspent.ui.theme.Motion
import org.umn.lensspent.ui.theme.Spacing
import org.umn.lensspent.ui.theme.motionTween

/**
 * The two pages a first launch walks through: what the app is and what it does with the user's data,
 * then the name and the ceiling.
 *
 * **What changed, and why.** The first page used to open with a 340dp coloured band containing the
 * app's name, with the three steps floating in a weighted, centred column underneath. On a tall phone
 * that left a third of the screen blank; on a short one, or with the system font enlarged, the
 * weight-based column had nowhere to give and the content competed with the button. This version is
 * a single scrolling column of normal document flow. It fills a short screen the same way it fills a
 * tall one, because nothing is vertically centred against a fixed band.
 *
 * The gradient is gone with it. The welcome page does not need a second colour system to say what the
 * app is; the wordmark, the one-sentence promise and the numbered loop do that, and they can be read
 * at any text size.
 *
 * [onFinished] is called once the profile write has landed, not when the button is tapped - the flag
 * that ends onboarding lives in the same transaction as the name and the budget, so the dashboard can
 * never be shown against a half-written profile.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished()
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    val direction = if (targetState == OnboardingStep.Profile) 1 else -1
                    // The welcome pages move on the same standard step as a pushed screen, so
                    // finishing onboarding does not change the feel of the app's motion.
                    (
                        fadeIn(motionTween(Motion.Standard, easing = Motion.Decelerate)) +
                            slideInHorizontally(
                                motionTween(Motion.Standard, easing = Motion.Decelerate)
                            ) { it / 8 * direction }
                        ) togetherWith fadeOut(
                        motionTween(Motion.Quick, easing = Motion.Accelerate)
                    )
                },
                label = "onboarding-step"
            ) { step ->
                when (step) {
                    OnboardingStep.Agreement -> AgreementPage(onAgree = viewModel::onAgree)
                    OnboardingStep.Profile -> ProfilePage(
                        username = uiState.username,
                        onUsernameChange = viewModel::onUsernameChange,
                        budgetText = uiState.budgetText,
                        monthlyBudgetIdr = uiState.monthlyBudgetIdr,
                        onBudgetChange = viewModel::onBudgetChange,
                        onBack = viewModel::onBackToAgreement,
                        onStartTracking = viewModel::startTracking
                    )
                }
            }
        }
    }
}

/**
 * Page one: what the app is, how it works in three steps, and the one way forward.
 *
 * The privacy facts still get said, in one footnote under the button. They are true, and they matter
 * to someone about to photograph their spending; they are not the headline, because nobody installs
 * a spending tracker to read about where their photos are processed.
 */
@Composable
private fun AgreementPage(onAgree: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xxl)
                .padding(top = Spacing.xxxl, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.xxl)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text(
                    text = "LensSpent",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Snap a receipt. Know where the month went.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                StepRow(
                    number = 1,
                    title = "Photograph the receipt",
                    detail = "Point the camera at it, or pick one from your gallery."
                )
                StepRow(
                    number = 2,
                    title = "Check what it read",
                    detail = "The shop, the total and the category are filled and editable."
                )
                StepRow(
                    number = 3,
                    title = "See where the month went",
                    detail = "A running total against your budget, and a breakdown by category."
                )
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            StepDots(activeIndex = 0)
            Button(
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = "Get started", style = MaterialTheme.typography.labelLarge)
            }
            Text(
                text = "Receipts are read on the phone and never uploaded. " +
                    "The optional AI chat is off by default; if you enable it, your question and " +
                    "monthly totals by category are sent, and only after you agree.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/**
 * One step in the loop, as a numbered marker beside a title and a line of explanation.
 *
 * A number rather than an icon: three icons would be three guesses at a metaphor, while 1-2-3 says
 * "this is the order you will do these in", which is what the screen is for.
 */
@Composable
private fun StepRow(number: Int, title: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Where the user is in the flow.
 *
 * Two steps, drawn as two dots with the current one stretched: with pages this short a numbered
 * stepper would be louder than the content, but a bare "1 of 2" is a sentence where a shape will do.
 */
@Composable
private fun StepDots(activeIndex: Int, count: Int = 2) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .width(if (index == activeIndex) 24.dp else 6.dp)
                    .height(6.dp)
                    .background(
                        color = if (index == activeIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * Page two: the name and the monthly budget, and the button that starts the ledger.
 *
 * Both fields may be left blank. The dashboard falls back to a generic greeting, and the budget card
 * only draws a progress line once there is a ceiling to draw it against.
 *
 * The two fields are in a grouped surface at the top of a scrolling column rather than centred
 * against the remaining height. That is what keeps the page whole when the keyboard is open on a
 * short phone: the button rides above the keyboard and the fields stay reachable above it.
 */
@Composable
private fun ProfilePage(
    username: String,
    onUsernameChange: (String) -> Unit,
    budgetText: String,
    monthlyBudgetIdr: Long?,
    onBudgetChange: (String) -> Unit,
    onBack: () -> Unit,
    onStartTracking: () -> Unit
) {
    val budgetIsReadable = monthlyBudgetIdr != null
    val budgetFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.sm, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.size(Spacing.xs))
                Text(text = "Back", style = MaterialTheme.typography.labelLarge)
            }
            StepDots(activeIndex = 1)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.xxl)
                .padding(top = Spacing.lg, bottom = Spacing.xxl),
            verticalArrangement = Arrangement.spacedBy(Spacing.lg)
        ) {
            Text(
                text = "Make it yours",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "A name for the greeting, and a monthly ceiling to measure against. " +
                    "Both are optional - you can set them later in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LensCard {
                Column(
                    modifier = Modifier.padding(Spacing.lg),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = onUsernameChange,
                        label = { Text("Your name") },
                        placeholder = { Text("Optional") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.Person,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        keyboardActions = KeyboardActions(
                            onNext = { budgetFocus.requestFocus() }
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = onBudgetChange,
                        label = { Text("Monthly budget") },
                        placeholder = { Text("Optional") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.AccountBalanceWallet,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        prefix = {
                            Text(
                                text = "Rp",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        singleLine = true,
                        isError = !budgetIsReadable,
                        supportingText = { Text(budgetSupportingText(budgetText, monthlyBudgetIdr)) },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = { if (budgetIsReadable) onStartTracking() }
                        ),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(budgetFocus)
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = Spacing.xxl, vertical = Spacing.xl)
        ) {
            Button(
                onClick = onStartTracking,
                enabled = budgetIsReadable,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                Text(text = "Start tracking", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
