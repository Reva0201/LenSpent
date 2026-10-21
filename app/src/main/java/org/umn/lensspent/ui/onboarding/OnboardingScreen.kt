package org.umn.lensspent.ui.onboarding

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.AppViewModelProvider
import org.umn.lensspent.ui.budgetSupportingText

private object OnboardingDimens {
    val Gutter = 24.dp
    val TitleSpacing = 8.dp
    val SectionSpacing = 32.dp
    val BulletSpacing = 20.dp
    val BulletIcon = 24.dp
    val BulletIconGap = 12.dp
    val BulletTitleSpacing = 2.dp
    val FieldSpacing = 24.dp
    val ButtonHeight = 52.dp
    val BottomSpacing = 24.dp
}

/**
 * The two pages a first launch walks through: what the app does with the user's data, then who it is
 * greeting and what they plan to spend.
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
                .padding(horizontal = OnboardingDimens.Gutter)
                // The name field sits low on a short screen, so the "Start Tracking" button below it
                // has to rise with the keyboard rather than hide behind it.
                .imePadding()
        ) {
            when (uiState.step) {
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

/**
 * Page one: the disclosure, then the one button that accepts it.
 *
 * Agreeing is what moves the user on, so there is no separate consent switch - the flow itself is the
 * record, and the only thing stored afterwards is that onboarding is done.
 */
@Composable
private fun AgreementPage(onAgree: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        // The block is centred in the space above the button rather than pinned to the top: with the
        // title and two cards it occupies about a third of a tall phone, and a top-aligned column left
        // a dead two-thirds between the last card and the only button on the screen.
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {

        // The app's mark, at size, because this is the one screen with room for it. Drawn as a
        // gradient disc rather than shipped as an image: it is the same primary-to-tertiary pair the
        // launcher icon uses, so the icon the user tapped and the screen they landed on agree.
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp)
            )
        }

        Spacer(Modifier.height(OnboardingDimens.SectionSpacing))
        Text(
            text = "Welcome to LensSpent",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Snap a receipt, and the ledger keeps itself.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OnboardingDimens.TitleSpacing)
        )

        Spacer(Modifier.height(OnboardingDimens.SectionSpacing))
        // The two disclosures as cards: they are separate promises, and separating them is what stops
        // the "sends text to DeepSeek" line from reading as a footnote to the local-only one.
        DisclosureCard(
            icon = Icons.Filled.Lock,
            title = "100% Local Core",
            detail = "Receipt photos never leave this device."
        )
        Spacer(Modifier.height(OnboardingDimens.BulletSpacing))
        DisclosureCard(
            icon = Icons.Filled.Cloud,
            title = "Premium Cloud AI",
            detail = "Asking AI for insights sends anonymized text to DeepSeek."
        )
        }

        // Pushed to the bottom: the button has to be where the thumb already is on a tall screen.
        Button(
            onClick = onAgree,
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.ButtonHeight),
            shape = MaterialTheme.shapes.large
        ) {
            Text(
                text = "Agree & Continue",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(OnboardingDimens.BottomSpacing))
    }
}

/** One disclosure, as a card: what it covers, then the plain-language consequence. */
@Composable
private fun DisclosureCard(icon: ImageVector, title: String, detail: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(OnboardingDimens.BulletIconGap + OnboardingDimens.BulletTitleSpacing),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                // The title beside it says the same thing, so the icon adds nothing for a screen reader.
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(OnboardingDimens.BulletIcon)
            )
            Spacer(Modifier.width(OnboardingDimens.BulletIconGap))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = OnboardingDimens.BulletTitleSpacing)
                )
            }
        }
    }
}

/**
 * Page two: the name and the monthly budget, and the button that starts the ledger.
 *
 * Both fields may be left blank. The dashboard falls back to a generic greeting, and the budget card
 * only draws a progress bar once there is a ceiling to draw it against - the app would rather say
 * nothing about a budget than put a number on screen that the user never chose.
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
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onBack) { Text("Back") }
        }
        Spacer(Modifier.height(OnboardingDimens.TitleSpacing))
        Text(
            text = "What should we call you?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Both of these stay on this device. Leave the name blank and the dashboard will just " +
                "say hello, or leave the budget blank and set it later in Settings.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OnboardingDimens.TitleSpacing)
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OnboardingDimens.FieldSpacing)
        )

        OutlinedTextField(
            value = budgetText,
            onValueChange = onBudgetChange,
            label = { Text("Monthly budget") },
            singleLine = true,
            prefix = { Text("Rp") },
            isError = !budgetIsReadable,
            supportingText = { Text(budgetSupportingText(budgetText, monthlyBudgetIdr)) },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            // "Done" starts the app too: by then the keyboard is covering the button, and making the
            // user dismiss it to reach the same action is a tap for nothing. Unreadable text is left
            // alone, exactly as the button is.
            keyboardActions = KeyboardActions(
                onDone = { if (budgetIsReadable) onStartTracking() }
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OnboardingDimens.FieldSpacing)
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStartTracking,
            enabled = budgetIsReadable,
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.ButtonHeight)
        ) {
            Text("Start Tracking")
        }
        Spacer(Modifier.height(OnboardingDimens.BottomSpacing))
    }
}
