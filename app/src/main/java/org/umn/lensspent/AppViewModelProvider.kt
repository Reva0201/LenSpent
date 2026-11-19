package org.umn.lensspent

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.umn.lensspent.data.repository.CaptureImageDeleter
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
        initializer {
            HomeViewModel(
                repository = application().container.transactionRepository,
                preferences = application().container.userPreferencesRepository
            )
        }
        initializer { AnalyticsViewModel(application().container.transactionRepository) }
        initializer { CameraViewModel(application().container.transactionRepository) }
        initializer {
            ExpenseReviewViewModel(
                repository = application().container.transactionRepository,
                extractor = application().container.captureExtractor,
                images = application().container.captureImageDeleter
            )
        }
        initializer {
            AiChatViewModel(
                insights = application().container.aiInsightsRepository,
                preferences = application().container.userPreferencesRepository,
                access = application().container.aiAccess
            )
        }
        initializer {
            OnboardingViewModel(application().container.userPreferencesRepository)
        }
        initializer {
            SettingsViewModel(
                preferences = application().container.userPreferencesRepository,
                ledger = application().container.transactionRepository,
                images = application().container.captureImageDeleter,
                receiptBytes = {
                    ImageStorage.storedBytes(application())
                },
                deleteReceiptImages = {
                    ImageStorage.deleteAll(application())
                }
            )
        }
        initializer { AddExpenseViewModel(application().container.transactionRepository) }
    }

    /**
     * A factory for the manual-entry form, which either creates a row or corrects one.
     *
     * [transactionId] is null for a new expense and the row's id when the user came from the detail
     * screen. Keyed by the caller so two forms in the back stack (one new, one editing) never share a
     * ViewModel and leak each other's draft.
     */
    fun addExpenseFactory(transactionId: Long? = null): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            AddExpenseViewModel(
                repository = application().container.transactionRepository,
                transactionId = transactionId
            )
        }
    }

    /**
     * A factory for the detail screen, which needs the row it was opened for.
     *
     * Built per id rather than added to [Factory] because the id is an argument, not a dependency: the
     * screen cannot be constructed meaningfully without it. The caller passes the same id as the
     * composable's `viewModel(key = ...)`, so two detail screens in the back stack never share one
     * ViewModel and observe each other's row.
     */
    fun transactionDetailFactory(transactionId: Long): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            TransactionDetailViewModel(
                repository = application().container.transactionRepository,
                transactionId = transactionId,
                images = application().container.captureImageDeleter
            )
        }
    }
}

private fun CreationExtras.application(): LensSpentApplication =
    checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) {
        "No Application in CreationExtras"
    } as LensSpentApplication
