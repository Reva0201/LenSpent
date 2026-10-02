package org.umn.lensspent.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.AppViewModelProvider

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
 * greeting.
 *
 * [onFinished] is called once the profile write has landed, not when the button is tapped - the flag
 * that ends onboarding lives in the same transaction as the name, so the dashboard can never be shown
 * against a half-written profile.
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
        Spacer(Modifier.height(OnboardingDimens.SectionSpacing))
        Text(
            text = "Welcome to LensSpent",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Text(
            text = "Snap a receipt, and the ledger keeps itself.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OnboardingDimens.TitleSpacing)
        )

        Spacer(Modifier.height(OnboardingDimens.SectionSpacing))
        DisclosureBullet(
            icon = Icons.Filled.Lock,
            title = "100% Local Core",
            detail = "Receipt photos never leave this device."
        )
        Spacer(Modifier.height(OnboardingDimens.BulletSpacing))
        DisclosureBullet(
            icon = Icons.Filled.Cloud,
            title = "Premium Cloud AI",
            detail = "Asking AI for insights sends anonymized text to DeepSeek."
        )

        // Pushed to the bottom: the button has to be where the thumb already is on a tall screen.
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onAgree,
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.ButtonHeight)
        ) {
            Text("Agree & Continue")
        }
        Spacer(Modifier.height(OnboardingDimens.BottomSpacing))
    }
}

/** One disclosure, as a labelled bullet: what it covers, then the plain-language consequence. */
@Composable
private fun DisclosureBullet(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = OnboardingDimens.BulletTitleSpacing)
            )
        }
    }
}

/**
 * Page two: the name, and the button that starts the ledger.
 *
 * The name may be left blank. The dashboard falls back to a generic greeting rather than the app
 * insisting on a name the user would rather not put on their own phone.
 */
@Composable
private fun ProfilePage(
    username: String,
    onUsernameChange: (String) -> Unit,
    onBack: () -> Unit,
    onStartTracking: () -> Unit
) {
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
            text = "Your name stays on this device and is only used to greet you. Leave it blank if " +
                "you would rather not say.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = OnboardingDimens.TitleSpacing)
        )

        OutlinedTextField(
            value = username,
            onValueChange = onUsernameChange,
            label = { Text("Your name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            // "Done" starts the app too: by then the keyboard is covering the button, and making the
            // user dismiss it to reach the same action is a tap for nothing.
            keyboardActions = KeyboardActions(onDone = { onStartTracking() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = OnboardingDimens.FieldSpacing)
        )

        Spacer(Modifier.weight(1f))
        Button(
            onClick = onStartTracking,
            modifier = Modifier
                .fillMaxWidth()
                .height(OnboardingDimens.ButtonHeight)
        ) {
            Text("Start Tracking")
        }
        Spacer(Modifier.height(OnboardingDimens.BottomSpacing))
    }
}
