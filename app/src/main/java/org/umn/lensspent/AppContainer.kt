package org.umn.lensspent

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.extraction.MlKitCaptureExtractor
import org.umn.lensspent.data.local.LensSpentDatabase
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.remote.AiAccess
import org.umn.lensspent.data.remote.AiBackendConfig
import org.umn.lensspent.data.remote.AiInsightsRepository
import org.umn.lensspent.data.remote.DeepSeekApiClient
import org.umn.lensspent.data.repository.CaptureImageDeleter
import org.umn.lensspent.data.repository.OfflineTransactionRepository
import org.umn.lensspent.data.repository.TransactionRepository

/**
 * Holds the singletons the app needs: the database and the repositories built on top of it, plus the
 * store of the settings the user chose.
 *
 * Everything is lazy, so simply constructing the container costs nothing and the database file is
 * only opened the first time something actually reads or writes spending data.
 */
class AppContainer(context: Context) {

    private val applicationContext: Context = context.applicationContext

    /**
     * The settings the user chose: whether onboarding is done, their name, and their theme.
     *
     * DataStore rather than Room, because these are a handful of switches rather than rows - and the app
     * root needs two of them before it can draw its first frame.
     */
    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(applicationContext)
    }

    private val database: LensSpentDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            LensSpentDatabase::class.java,
            LensSpentDatabase.Name
        )
            // Deliberately no fallbackToDestructiveMigration: spending history is user data, so a
            // missing migration must fail loudly in development instead of silently wiping the ledger.
            .build()
    }

    val transactionRepository: TransactionRepository by lazy {
        OfflineTransactionRepository(
            transactionDao = database.transactionDao(),
            categoryDao = database.categoryDao(),
            captureDao = database.captureDao()
        )
    }

    /**
     * Deletes a capture's image bytes once the row that owned it is gone.
     *
     * Kept out of the repository (which has no `Context`) and out of the ViewModels (which have no
     * business knowing where files live), so "confirmed/discarded/deleted receipts leave no orphan
     * photo" is one decision in one place.
     */
    val captureImageDeleter: CaptureImageDeleter = CaptureImageDeleter { uri ->
        ImageStorage.deleteReceipt(applicationContext, uri)
    }

    /**
     * The on-device extraction pipeline: ML Kit OCR and the fast-path parser. Exposing it as a [CaptureExtractor] is what keeps
     * every screen and ViewModel unaware of how many stages run behind it.
     */
    val captureExtractor: CaptureExtractor by lazy {
        MlKitCaptureExtractor(
            context = applicationContext,
            repository = transactionRepository
        )
    }

    /**
     * The secure backend the AI feature talks to, if this build was given one.
     *
     * Null is the normal case for a source checkout and for the shipped default: the app has no
     * provider credential to embed, so with no proxy configured there is nothing to call.
     */
    private val aiApi by lazy { DeepSeekApiClient.createOrNull() }

    /**
     * The three gates that decide whether one AI request may leave the phone.
     *
     * Read live from DataStore on every request, so switching AI off in Settings (or a future consent
     * revocation) applies to the very next question rather than the next launch.
     */
    val aiAccess: Flow<AiAccess> =
        combine(
            userPreferencesRepository.aiConsentGranted,
            userPreferencesRepository.aiNetworkEnabled
        ) { consent, enabled ->
            AiAccess(
                configured = AiBackendConfig.isConfigured && aiApi != null,
                enabled = enabled,
                consentGranted = consent
            )
        }

    /**
     * The AI feature's data layer.
     *
     * It is built on the same [transactionRepository] the dashboard reads, so the assistant can never
     * describe a different ledger from the one on screen. What it may transmit is decided one layer
     * down in `toInsightMonths` - monthly, category-level aggregates only.
     */
    val aiInsightsRepository: AiInsightsRepository by lazy {
        AiInsightsRepository(
            transactions = transactionRepository,
            api = aiApi,
            access = {
                // One-shot reads: the repository asks at the moment of the request, which is the only
                // moment the answer is guaranteed to reflect what the user just chose.
                AiAccess(
                    configured = AiBackendConfig.isConfigured && aiApi != null,
                    enabled = userPreferencesRepository.aiNetworkEnabled.first(),
                    consentGranted = userPreferencesRepository.aiConsentGranted.first()
                )
            }
        )
    }
}
