package org.umn.lensspent

import android.content.Context
import androidx.room.Room
import org.umn.lensspent.data.extraction.CaptureExtractor
import org.umn.lensspent.data.extraction.MlKitCaptureExtractor
import org.umn.lensspent.data.local.LensSpentDatabase
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.data.remote.AiInsightsRepository
import org.umn.lensspent.data.remote.DeepSeekApiClient
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
     * Premium AI Insights: the one collaborator here that leaves the phone.
     *
     * It is built on the same [transactionRepository] the dashboard reads, so the assistant can never
     * describe a different ledger from the one on screen. Its key is the one this build was compiled
     * with - see [DeepSeekApiClient.ApiKey] - and a build without one is not an error either: the
     * repository turns the missing key into a sentence the chat can show. The clock is left at its
     * default, so the date the assistant is told is the phone's own - which is the one the ledger was
     * written in.
     */
    val aiInsightsRepository: AiInsightsRepository by lazy {
        AiInsightsRepository(
            transactions = transactionRepository,
            api = DeepSeekApiClient.create(),
            apiKeyConfigured = DeepSeekApiClient.isConfigured
        )
    }
}
