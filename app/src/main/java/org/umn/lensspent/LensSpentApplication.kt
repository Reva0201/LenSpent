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
        }
    }
}
