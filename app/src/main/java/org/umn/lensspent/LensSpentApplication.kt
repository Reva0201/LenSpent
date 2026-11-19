package org.umn.lensspent

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.umn.lensspent.data.demo.DemoLedgerSeeder

/**
 * Process-wide wiring. Manual dependency injection on purpose: one container, no annotation processor,
 * which keeps the offline data path easy to follow end to end.
 */
class LensSpentApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * Survives any single screen - used for work that must finish on its own, starting with seeding
     * the taxonomy on first launch and later for background pipeline jobs.
     */
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Room's Flow queries emit again on their own once this lands, so the screens can subscribe first.
        applicationScope.launch {
            container.transactionRepository.seedIfEmpty()
            // The demo ledger is a build-time decision, not a runtime one: a build compiled without it
            // starts empty and stays empty, which is what the app does for a real user.
            if (BuildConfig.DEMO_LEDGER) {
                DemoLedgerSeeder(container.transactionRepository).seedIfEmpty()
            }
            // Receipt images are written before the capture row that names them, so a crash, a
            // cancelled extraction or a killed process can leave a private file nothing points at.
            // Sweeping at startup closes that window without touching a file a live capture owns.
            sweepOrphanedReceiptImages()
        }
    }

    /**
     * Removes receipt files no capture row references.
     *
     * Only files inside the app's own `receipts/` directory can be affected: the sweep asks
     * `ImageStorage`, which refuses any path outside it. A failure here is not worth surfacing - the
     * sweep runs again next launch - but it is recorded in debug builds.
     */
    private suspend fun sweepOrphanedReceiptImages() {
        runCatching {
            val referenced = container.transactionRepository.captureImageUris().toSet()
            ImageStorage.deleteOrphans(this, isReferenced = { uri -> uri in referenced })
        }.onFailure { failure ->
            AppLog.warn("receipt image sweep failed", failure)
        }
    }
}
