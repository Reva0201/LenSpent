package org.umn.lensspent.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
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
    fun `a fresh install follows the phone's theme and has no name`() = runBlocking {
        val repository = repository()

        assertEquals(UserPreferences(), repository.preferences.first())

        assertFalse(repository.hasCompletedOnboarding.first())
        assertEquals("", repository.username.first())
        assertEquals(ThemePreference.SYSTEM, repository.themePreference.first())
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
    fun `finishing onboarding stores the name and the flag together`() = runBlocking {
        val repository = repository()

        repository.saveOnboardingProfile("Sari")

        assertEquals(
            UserPreferences(hasCompletedOnboarding = true, username = "Sari"),
            repository.preferences.first()
        )
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

    private fun repository(): UserPreferencesRepository = UserPreferencesRepository(dataStore())

    private fun dataStore(): DataStore<Preferences> = PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = {
            // Deliberately not created first: DataStore expects to write the file itself.
            File(temporaryFolder.root, "user_preferences_${nextFile++}.preferences_pb")
        }
    )
}
