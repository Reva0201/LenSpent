package org.umn.lensspent.ui.settings

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.umn.lensspent.AppViewModelProvider
import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.local.ThemePreference
import org.umn.lensspent.formatIDR
import org.umn.lensspent.ui.budgetSupportingText
import org.umn.lensspent.ui.parseBudgetInput
import org.umn.lensspent.ui.theme.Elevation
import org.umn.lensspent.ui.theme.Sizes
import org.umn.lensspent.ui.theme.Spacing

private object SettingsDimens {
    val Gutter = 16.dp
    /** Room above a section header, so the settings read as groups rather than as one long list. */
    val SectionSpacing = 20.dp
    val ItemSpacing = 4.dp
    val FieldSpacing = 8.dp
    val BottomSpacing = 32.dp
}

/**
 * Everything the user can change about the app: who they are, what their monthly ceiling is, how it
 * looks, and how to get their ledger out.
 *
 * The screen holds no settings of its own. It draws [SettingsUiState] from the store and writes back
 * through the view model, so the state after a change is the state that was stored - there is no second
 * copy here to fall out of step with DataStore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val pendingCsvExport by viewModel.pendingCsvExport.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // The export leaves through the system share sheet, so there is no folder to pick and no storage
    // permission to ask for: the user chooses the destination, and the file is written by whatever
    // app they choose.
    LaunchedEffect(pendingCsvExport) {
        val csv = pendingCsvExport ?: return@LaunchedEffect
        context.startActivity(
            Intent.createChooser(transactionsCsvIntent(csv), "Export transactions")
        )
        viewModel.onCsvExportHandled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = SettingsDimens.Gutter)
                .padding(top = Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.ItemSpacing)
        ) {
            ProfileHeader(username = uiState.username)

            // Directly under the name, the way onboarding asked for them: the ceiling is the one
            // setting on this screen that the dashboard draws with.
            SectionHeader("Budget")
            SettingsCard {
                BudgetSection(
                    monthlyBudgetIdr = uiState.monthlyBudgetIdr,
                    onBudgetConfirmed = viewModel::onBudgetConfirmed
                )
            }

            SectionHeader("Preferences")
            SettingsCard {
                PreferencesSection(
                    themePreference = uiState.themePreference,
                    onThemeSelected = viewModel::onThemeSelected
                )
            }

            SectionHeader("Data")
            SettingsCard { DataSection(onExportTransactions = viewModel::exportTransactions) }

            Spacer(Modifier.height(SettingsDimens.SectionSpacing))
            Text(
                text = "LensSpent 1.0 — your ledger stays on this device",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(SettingsDimens.BottomSpacing))
        }
    }
}

/**
 * A group label: small, upper-case, and quiet enough not to compete with the rows under it.
 *
 * It used to be primary-coloured, which put four bright blue labels on a page of otherwise quiet rows.
 * Muted grey keeps the hierarchy - the label still starts a group - without four competing accents.
 */
@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = SettingsDimens.SectionSpacing, bottom = SettingsDimens.ItemSpacing)
    )
}

/**
 * One group of settings, as a card.
 *
 * The rows were bare `ListItem`s, which drew full-width white blocks with square corners on a page
 * whose every other surface is a rounded card - the settings screen looked like a different app. A card
 * per group also makes the grouping real: the rows inside one belong to one subject.
 */
@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        // Hairline rather than shadow: see LensCard. One outlined block per group.
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        ),
        shadowElevation = Elevation.card
    ) {
        Column(modifier = Modifier.padding(vertical = Spacing.xs)) {
            content()
        }
    }
}

/** Who the app greets, as a header rather than a row: this screen is about one person's ledger. */
@Composable
private fun ProfileHeader(username: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.xl),
            horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = username.trim().firstOrNull()?.uppercase() ?: "L",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Text(
                    text = username.ifBlank { "No name set" },
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Local profile · never leaves this device",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * One settings row: a tinted icon square, a title, an optional line of supporting text, and whatever
 * trailing affordance the row needs.
 *
 * `ListItem` was doing this job before and was the reason the screen felt like a dump: it sets the
 * headline at body size, adds 72dp of leading space for the icon, and pads every row the same whether
 * it carries a sentence or three words. A row that is 48dp tall and one line wide reads as a setting;
 * one that is 90dp tall reads as a paragraph.
 */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    supporting: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.iconSmall)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (supporting != null) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        trailing?.invoke()
    }
}

@Composable
private fun RowChevron() {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * The monthly ceiling: the amount the dashboard measures against, and the dialog that changes it.
 *
 * A dialog rather than a field on the page, because the page has no save button - a field here would
 * have to write on every keystroke, and a half-typed number is not an amount. Backing out changes
 * nothing.
 */
@Composable
private fun BudgetSection(
    monthlyBudgetIdr: Long,
    onBudgetConfirmed: (Long) -> Unit
) {
    var isEditing by remember { mutableStateOf(false) }

    SettingsRow(
        icon = Icons.Filled.AccountBalanceWallet,
        title = if (monthlyBudgetIdr > NoBudgetIdr) {
            "${formatIDR(monthlyBudgetIdr)} a month"
        } else {
            "No budget set"
        },
        supporting = "The ceiling the dashboard measures against",
        trailing = { RowChevron() },
        onClick = { isEditing = true }
    )

    if (isEditing) {
        BudgetDialog(
            storedBudgetIdr = monthlyBudgetIdr,
            onDismiss = { isEditing = false },
            onConfirm = { amount ->
                onBudgetConfirmed(amount)
                isEditing = false
            }
        )
    }
}

/**
 * The field the ceiling is typed into.
 *
 * The app's one editable number, so its draft lives here rather than in the store. The text is seeded
 * once from what was stored and then left alone: reading the store back on every frame would rewrite
 * the value as it is saved - swallowing separators as they are typed, or moving the cursor under the
 * user's finger - and a number being typed is not a number yet.
 */
@Composable
private fun BudgetDialog(
    storedBudgetIdr: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    var text by remember {
        mutableStateOf(if (storedBudgetIdr > NoBudgetIdr) storedBudgetIdr.toString() else "")
    }
    val parsed = parseBudgetInput(text)

    AlertDialog(
        // Back, or a tap outside: nothing has been written yet, so there is nothing to undo.
        onDismissRequest = onDismiss,
        title = { Text("Monthly budget") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Amount") },
                singleLine = true,
                prefix = { Text("Rp") },
                isError = parsed == null,
                supportingText = { Text(budgetSupportingText(text, parsed)) },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                // "Done" saves, as the button does: unreadable text leaves the dialog open, which is
                // the same thing the disabled button says.
                keyboardActions = KeyboardActions(onDone = { parsed?.let(onConfirm) }),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { parsed?.let(onConfirm) }, enabled = parsed != null) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/** Look and language: the two things about the app that are not about the spending. */
@Composable
private fun PreferencesSection(
    themePreference: ThemePreference,
    onThemeSelected: (ThemePreference) -> Unit
) {
    SettingsRow(
        icon = Icons.Filled.Palette,
        title = "Theme",
        supporting = "Light, dark, or the phone's setting"
    )
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp, end = Spacing.lg, bottom = Spacing.md)
    ) {
        val options = ThemePreference.entries
        options.forEachIndexed { index, preference ->
            SegmentedButton(
                selected = preference == themePreference,
                onClick = { onThemeSelected(preference) },
                // Rounded to the same radius as the cards, instead of Material's square-cornered
                // default, which cut across the bottom of the card it sits in.
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = MaterialTheme.shapes.small
                )
            ) {
                Text(
                    text = preference.label(),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    SettingsRow(
        icon = Icons.Filled.Language,
        title = "Language",
        trailing = {
            Text(
                text = "English",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    )
}

/** What each stored preference is called on screen - the enum's own values are storage, not prose. */
private fun ThemePreference.label(): String = when (this) {
    ThemePreference.SYSTEM -> "System"
    ThemePreference.LIGHT -> "Light"
    ThemePreference.DARK -> "Dark"
}

/** Getting the ledger out: the one row here that leaves the app, and it only ever does so on request. */
@Composable
private fun DataSection(onExportTransactions: () -> Unit) {
    SettingsRow(
        icon = Icons.Filled.Share,
        title = "Export transactions to CSV",
        supporting = "Every expense as a CSV file",
        trailing = { RowChevron() },
        onClick = onExportTransactions
    )
}

/**
 * The hand-off for an export: the whole CSV as the text of a share intent.
 *
 * Text rather than a written file, because a file needs a destination and, on older releases, a
 * storage permission to have one - while a text share reaches every target the user could want: mail,
 * a note, Drive, a spreadsheet app. The typed MIME is `text/plain` on purpose: most receivers, mail
 * clients above all, declare only that, and a CSV payload is text they will happily carry. Calling it
 * `text/csv` would hide the export from the apps people actually send things with.
 *
 * `NEW_TASK` is belt and braces - the caller is an activity today, and this keeps the same intent valid
 * if it is ever started from an application context, where the flag is required rather than optional.
 */
internal fun transactionsCsvIntent(csv: String): Intent = Intent(Intent.ACTION_SEND).apply {
    type = "text/plain"
    putExtra(Intent.EXTRA_SUBJECT, "LensSpent transactions")
    putExtra(Intent.EXTRA_TEXT, csv)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

