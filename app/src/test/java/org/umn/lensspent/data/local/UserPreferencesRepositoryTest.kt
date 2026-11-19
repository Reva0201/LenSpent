package org.umn.lensspent.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The settings store, over a real DataStore file in a temporary folder.
 *
 * A real one rather than a fake, because what this class does is the round trip: a value chosen in
 * Settings or onboarding has to come back out of the file as the same value, including when the phone
 * writes a string this version of the app does not recognise.
 */
class UserPreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** A fresh store each time: two DataStores over one file is an error DataStore refuses. */
    private var nextFile = 0

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `a fresh install follows the phone's theme and has no name or budget`() = runBlocking {
        val repository = repository()

        assertEquals(UserPreferences(), repository.preferences.first())

        assertFalse(repository.hasCompletedOnboarding.first())
        assertEquals("", repository.username.first())
        assertEquals(ThemePreference.SYSTEM, repository.themePreference.first())
        assertEquals(NoBudgetIdr, repository.monthlyBudgetIdr.first())
    }

    @Test
    fun `a name comes back trimmed`() = runBlocking {
        val repository = repository()

        repository.setUsername("  Ibu Sari  ")

        assertEquals("Ibu Sari", repository.username.first())
    }

    /**
     * The stored form is pinned by name, not by the enum's ordinal: the file outlives the code, and one
     * day a fourth option will be inserted between two of these three.
     */
    @Test
    fun `a theme is stored under its own name rather than its position`() = runBlocking {
        val store = dataStore()
        val repository = UserPreferencesRepository(store)

        repository.setThemePreference(ThemePreference.DARK)

        assertEquals(ThemePreference.DARK, repository.themePreference.first())
        assertEquals("DARK", store.data.first()[stringPreferencesKey("theme_preference")])
    }

    /** A value only a newer build could have written: this one reads it as "follow the phone". */
    @Test
    fun `an unrecognised theme falls back to the phone's setting`() = runBlocking {
        val store = dataStore()
        store.edit { it[stringPreferencesKey("theme_preference")] = "SEPIA" }

        val repository = UserPreferencesRepository(store)

        assertEquals(ThemePreference.SYSTEM, repository.themePreference.first())
    }

    @Test
    fun `finishing onboarding stores the name, the ceiling and the flag together`() = runBlocking {
        val repository = repository()

        repository.saveOnboardingProfile("Sari", 4_500_000L)

        assertEquals(
            UserPreferences(
                hasCompletedOnboarding = true,
                username = "Sari",
                monthlyBudgetIdr = 4_500_000L
            ),
            repository.preferences.first()
        )
    }

    /** Skipping the budget is a real answer, and the only one the dashboard may not invent for itself. */
    @Test
    fun `finishing onboarding with the budget left blank stores no ceiling`() = runBlocking {
        val repository = repository()

        repository.saveOnboardingProfile("Sari", NoBudgetIdr)

        assertEquals(
            UserPreferences(hasCompletedOnboarding = true, username = "Sari"),
            repository.preferences.first()
        )
    }

    /**
     * A number under its own key, not text: the dashboard reads this as an amount, and a value stored
     * as "3.750.000" would come back needing the parser that wrote it.
     */
    @Test
    fun `a budget is stored as whole Rupiah under its own key`() = runBlocking {
        val store = dataStore()
        val repository = UserPreferencesRepository(store)

        repository.setMonthlyBudgetIdr(3_750_000L)

        assertEquals(3_750_000L, store.data.first()[longPreferencesKey("monthly_budget_idr")])
    }

    /** A ceiling set in Settings is not a one-way door: it has to be changeable and removable. */
    @Test
    fun `a budget set later can be changed and cleared again`() = runBlocking {
        val repository = repository()

        repository.setMonthlyBudgetIdr(3_750_000L)
        assertEquals(3_750_000L, repository.monthlyBudgetIdr.first())

        repository.setMonthlyBudgetIdr(5_000_000L)
        assertEquals(5_000_000L, repository.monthlyBudgetIdr.first())

        repository.setMonthlyBudgetIdr(NoBudgetIdr)
        assertEquals(NoBudgetIdr, repository.monthlyBudgetIdr.first())
    }

    @Test
    fun `the flag can be settled on its own once the profile is known`() = runBlocking {
        val repository = repository()

        repository.setHasCompletedOnboarding(true)

        assertEquals(
            UserPreferences(hasCompletedOnboarding = true),
            repository.preferences.first()
        )
    }

    /**
     * The AI gate must start closed. A fresh install, or an upgrade from a build that had no consent
     * concept, has no acceptance on disk - and the repository's default is what decides that.
     */
    @Test
    fun `AI consent is not granted on a fresh install`() = runBlocking {
        val repository = repository()

        assertFalse(repository.aiConsentGranted.first())
        assertFalse(repository.preferences.first()!!.aiConsentGranted)
    }

    @Test
    fun `the AI networking switch defaults to on, but consent still gates it`() = runBlocking {
        val repository = repository()

        assertTrue(repository.aiNetworkEnabled.first())
        assertFalse(
            "the switch alone must never be enough",
            repository.preferences.first()!!.aiConsentGranted
        )
    }

    @Test
    fun `consent survives a restart and can be revoked`() = runBlocking {
        val file = File(temporaryFolder.root, "consent-${nextFile++}.preferences_pb")
        val store = dataStoreAt(file)

        UserPreferencesRepository(store).setAiConsentGranted(true)
        // A second repository over the same bytes is what a process restart looks like.
        assertTrue(UserPreferencesRepository(store).aiConsentGranted.first())

        UserPreferencesRepository(store).setAiConsentGranted(false)
        assertFalse(UserPreferencesRepository(store).aiConsentGranted.first())
    }

    @Test
    fun `turning AI networking off does not erase the acceptance record`() = runBlocking {
        val repository = repository()
        repository.setAiConsentGranted(true)

        repository.setAiNetworkEnabled(false)

        assertFalse(repository.aiNetworkEnabled.first())
        assertTrue(
            "the switch and the acceptance are separate decisions",
            repository.aiConsentGranted.first()
        )
    }

    private fun repository(): UserPreferencesRepository = UserPreferencesRepository(dataStore())

    private fun dataStore(): DataStore<Preferences> = dataStoreAt(
        File(temporaryFolder.root, "user_preferences_${nextFile++}.preferences_pb")
    )

    private fun dataStoreAt(file: File): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
}
