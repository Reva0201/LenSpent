package org.umn.lensspent.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.AppViewModelProvider
import org.umn.lensspent.ui.budgetSupportingText
import org.umn.lensspent.ui.components.LensCard
import org.umn.lensspent.ui.theme.LocalHeroColors
import org.umn.lensspent.ui.theme.Spacing

private object OnboardingDimens {
    val Gutter = 28.dp
    val HeroHeight = 340.dp
    val ButtonHeight = 56.dp
    val PromiseIcon = 40.dp
    val DotWidth = 24.dp
    val DotHeight = 6.dp
}

/**
 * The two pages a first launch walks through: what the app is and what it does with the user's data,
 * then the name and the ceiling.
 *
 * The shape is deliberate. Page one is a launch screen, not a consent form: a brand band, two promises,
 * one button. Page two is the only place the app asks for anything, so it asks for two things and says
 * that both are optional.
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
                // The budget field sits low on a short screen, so the button below it has to rise with
                // the keyboard rather than hide behind it.
                .imePadding()
        ) {
            AnimatedContent(
                targetState = uiState.step,
                transitionSpec = {
                    // Page-turn rather than a cut: the incoming page slides in from the direction the
                    // flow is heading and the outgoing one fades underneath it. Both directions animate,
                    // so going Back does not feel like a different app from going forward.
                    val direction = if (targetState == OnboardingStep.Profile) 1 else -1
                    (
                        fadeIn(animationSpec = tween(240)) +
                            slideInHorizontally(animationSpec = tween(240)) { it / 8 * direction }
                        ) togetherWith fadeOut(animationSpec = tween(160))
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
 * Page one: the brand, the two promises, and the one way forward.
 *
 * A tinted band across the top rather than a logo floating in white space: this is the first frame the
 * app ever draws, and it should look like a product before it looks like a form. The two promises are
 * rows with their own icon squares, not cards, because cards here read as sections of a settings page.
 */
@Composable
private fun AgreementPage(onAgree: () -> Unit) {
    val hero = LocalHeroColors.current

    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.HeroHeight)
                .clip(
                    RoundedCornerShape(
                        bottomStart = 36.dp,
                        bottomEnd = 36.dp
                    )
                )
                .background(Brush.verticalGradient(listOf(hero.start, hero.end)))
                .padding(horizontal = OnboardingDimens.Gutter, vertical = Spacing.xxxl)
        ) {
            Column(modifier = Modifier.align(Alignment.BottomStart)) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(hero.content.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = hero.content,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(Modifier.height(Spacing.xl))
                Text(
                    text = "LensSpent",
                    style = MaterialTheme.typography.displaySmall,
                    color = hero.content
                )
                Spacer(Modifier.height(Spacing.sm))
                Text(
                    text = "Snap a receipt. Know where the month went.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = hero.contentMuted
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OnboardingDimens.Gutter),
            // Centred in what is left of the page rather than pinned under the band: two rows of prose
            // stranded at the top left left the bottom half of the screen empty.
            verticalArrangement = Arrangement.spacedBy(
                space = Spacing.xxl,
                alignment = Alignment.CenterVertically
            )
        ) {
            // The promises share one bordered surface. On their own they were two lines of grey prose
            // floating in white space; in a card they read as one statement about the app, and the page
            // has a middle instead of a gap.
            LensCard {
                Column(
                    modifier = Modifier.padding(Spacing.xl),
                    verticalArrangement = Arrangement.spacedBy(Spacing.lg)
                ) {
                    PromiseRow(
                        icon = Icons.Filled.Lock,
                        title = "Read on this device",
                        detail = "Receipt photos are processed on the phone and never uploaded."
                    )
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
                    )
                    PromiseRow(
                        icon = Icons.Filled.Cloud,
                        title = "AI is opt-in",
                        detail = "Only the AI insights chat sends anonymised text to DeepSeek."
                    )
                }
            }
        }

        Column(
            modifier = Modifier.padding(
                horizontal = OnboardingDimens.Gutter,
                vertical = OnboardingDimens.Gutter
            ),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl)
        ) {
            StepDots(activeIndex = 0)
            Button(
                onClick = onAgree,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingDimens.ButtonHeight),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "Get started",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

/** One promise: what it covers, then the plain-language consequence. */
@Composable
private fun PromiseRow(icon: ImageVector, title: String, detail: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(OnboardingDimens.PromiseIcon)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                // The title beside it says the same thing, so the icon adds nothing for a screen reader.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Where the user is in the flow.
 *
 * Two steps, drawn as two dots with the current one stretched: with pages this short a numbered stepper
 * would be louder than the content, but a bare "1 of 2" is a sentence where a shape will do.
 */
@Composable
private fun StepDots(activeIndex: Int, count: Int = 2) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .height(OnboardingDimens.DotHeight)
                    .width(if (index == activeIndex) OnboardingDimens.DotWidth else OnboardingDimens.DotHeight)
                    .clip(CircleShape)
                    .background(
                        if (index == activeIndex) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        }
                    )
            )
        }
    }
}

/**
 * Page two: the name and the monthly budget, and the button that starts the ledger.
 *
 * Both fields may be left blank. The dashboard falls back to a generic greeting, and the budget card
 * only draws a progress line once there is a ceiling to draw it against - the app would rather say
 * nothing about a budget than put a number on screen that the user never chose. The page says so in one
 * line instead of the paragraph it used to open with.
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
    // Only unreadable text blocks the button, and clearing the field always clears that, so this can
    // never strand the user on this page - it just refuses to throw a number away.
    val budgetIsReadable = monthlyBudgetIdr != null
    val usernameFocus = remember { FocusRequester() }
    val budgetFocus = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.md),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) {
                Text(
                    text = "Back",
                    style = MaterialTheme.typography.labelLarge
                )
            }
            StepDots(activeIndex = 1)
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = OnboardingDimens.Gutter),
            // Centred between the header and the button, the same as page one: two fields pinned to the
            // top left the last half of the screen blank.
            verticalArrangement = Arrangement.spacedBy(
                space = Spacing.lg,
                alignment = Alignment.CenterVertically
            )
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

            Spacer(Modifier.height(Spacing.md))

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
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                // "Next" has to actually move: the key was declared but nothing listened to it, so
                // pressing it left the caret in the name field and the budget then arrived there.
                keyboardActions = KeyboardActions(
                    onNext = { budgetFocus.requestFocus() }
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(usernameFocus)
            )

            OutlinedTextField(
                value = budgetText,
                onValueChange = onBudgetChange,
                label = { Text("Monthly budget") },
                placeholder = { Text("Optional") },
                singleLine = true,
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
                isError = !budgetIsReadable,
                supportingText = { Text(budgetSupportingText(budgetText, monthlyBudgetIdr)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                // "Done" starts the app too: by then the keyboard is covering the button, and making
                // the user dismiss it to reach the same action is a tap for nothing. Unreadable text
                // is left alone, exactly as the button is.
                keyboardActions = KeyboardActions(
                    onDone = { if (budgetIsReadable) onStartTracking() }
                ),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(budgetFocus)
            )
        }

        Column(
            modifier = Modifier.padding(
                horizontal = OnboardingDimens.Gutter,
                vertical = OnboardingDimens.Gutter
            )
        ) {
            Button(
                onClick = onStartTracking,
                enabled = budgetIsReadable,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OnboardingDimens.ButtonHeight),
                shape = MaterialTheme.shapes.large
            ) {
                Text(
                    text = "Start tracking",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}
