package org.umn.lensspent

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.umn.lensspent.ui.onboarding.OnboardingViewModel
import org.umn.lensspent.ui.settings.SettingsViewModel

/** Screens stop observing this long after they leave the screen, then resume from Room. */
internal const val SubscriptionStopTimeoutMillis = 5_000L

/**
 * Single factory for every screen ViewModel, wired straight to [AppContainer]. Screens only need
 * `viewModel(factory = AppViewModelProvider.Factory)`.
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {
        initializer { HomeViewModel(application().container.transactionRepository) }
        initializer { AnalyticsViewModel(application().container.transactionRepository) }
        initializer { CameraViewModel(application().container.transactionRepository) }
        initializer {
            ExpenseReviewViewModel(
                repository = application().container.transactionRepository,
                extractor = application().container.captureExtractor
            )
        }
        initializer { AiChatViewModel(application().container.aiInsightsRepository) }
        initializer {
            OnboardingViewModel(application().container.userPreferencesRepository)
        }
        initializer {
            SettingsViewModel(
                preferences = application().container.userPreferencesRepository,
                ledger = application().container.transactionRepository
            )
        }
    }
}

private fun CreationExtras.application(): LensSpentApplication =
    checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "No Application in CreationExtras"
    } as LensSpentApplication
