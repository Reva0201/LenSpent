package org.umn.lensspent.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/** The file inside the app's private storage. One DataStore per process per name. */
private const val DataStoreFileName = "user_preferences"

/**
 * The preferences store, created the first time something reads it.
 *
 * DataStore requires one instance per file name, and a top-level delegate is the only shape that
 * guarantees it: nothing outside this file can build a second store over the same file and end up
 * with the two disagreeing about what was written last.
 */
private val Context.userPreferencesDataStore: DataStore<Preferences> by
    preferencesDataStore(name = DataStoreFileName)

/**
 * The three answers the theme setting can hold.
 *
 * Stored as its [storageValue] string - a file that outlives the code has no business depending on
 * the order of an enum - and surfaced as the enum itself, so the root can switch on it exhaustively
 * instead of comparing strings.
 */
enum class ThemePreference(val storageValue: String) {
    SYSTEM("SYSTEM"),
    LIGHT("LIGHT"),
    DARK("DARK");

    companion object {
        val Default: ThemePreference = ThemePreference.SYSTEM

        /** Reads a stored value, falling back to [Default] for anything absent or unrecognised. */
        fun fromStorage(value: String?): ThemePreference =
            entries.firstOrNull { it.storageValue == value } ?: Default
    }
}

/**
 * No monthly ceiling has been chosen.
 *
 * Zero rather than a plausible-looking number: the dashboard greets a budget the user set and says
 * nothing about one they did not, and an invented default would put a "budget" on screen that was never
 * anyone's. It is also why this is a named constant - "no budget" is a state, not an amount.
 */
const val NoBudgetIdr: Long = 0L

/** Everything stored, with the values a fresh install starts from. */
data class UserPreferences(
    val hasCompletedOnboarding: Boolean = false,
    val username: String = "",
    val themePreference: ThemePreference = ThemePreference.SYSTEM,
    /**
     * The monthly ceiling the user typed, in whole Rupiah, or [NoBudgetIdr] if they left it blank.
     *
     * It lives here rather than in the ledger because it is a choice about the app's behaviour, not a
     * spending row: the dashboard needs it on every frame, and it is not something the user spends.
     */
    val monthlyBudgetIdr: Long = NoBudgetIdr
)

/**
 * The settings the user chose, as flows.
 *
 * These are not spending data, so they sit outside Room: the app root reads two of them before it
 * draws anything at all (which screen to start on, and in which theme), and the settings screen reads
 * the rest. Every write is a DataStore transaction and every read is a [Flow] that re-emits the moment
 * a write lands - which is what makes a theme chosen in Settings take effect on the screen behind the
 * sheet, with no restart.
 *
 * A [DataStore] rather than a [Context] is the constructor's real argument, so the whole class can be
 * exercised in a unit test over a temporary file.
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    /** The production store: the app's own private DataStore file. */
    constructor(context: Context) : this(context.userPreferencesDataStore)

    /**
     * The stored bytes, with a corrupt file degraded to empty rather than fatal.
     *
     * Every value here has a default a fresh install already uses, so the worst a damaged file can
     * cost the user is choosing their name, their budget and their theme again. Crashing on launch,
     * before the first frame is drawn, would cost them the app.
     */
    private val stored: Flow<Preferences> = dataStore.data.catch { failure ->
        if (failure is IOException) emit(emptyPreferences()) else throw failure
    }

    /** False until the user has been through onboarding; the app starts there while it is false. */
    val hasCompletedOnboarding: Flow<Boolean> = stored.map { it[Keys.HasCompletedOnboarding] ?: false }

    val username: Flow<String> = stored.map { it[Keys.Username] ?: "" }

    val themePreference: Flow<ThemePreference> =
        stored.map { ThemePreference.fromStorage(it[Keys.ThemePreference]) }

    /** Whole Rupiah, or [NoBudgetIdr] when the user has not chosen a ceiling. */
    val monthlyBudgetIdr: Flow<Long> = stored.map { it[Keys.MonthlyBudgetIdr] ?: NoBudgetIdr }

    /**
     * All of it at once, or null until the first read lands.
     *
     * The null is the whole reason this sits next to the per-key flows above: those can only answer
     * with their default, and a default is indistinguishable from a stored default. The app root has
     * to tell those two apart to avoid showing onboarding to a user who finished it weeks ago.
     */
    val preferences: Flow<UserPreferences?> = stored.map { it.toUserPreferences() }

    suspend fun setHasCompletedOnboarding(completed: Boolean) {
        dataStore.edit { it[Keys.HasCompletedOnboarding] = completed }
    }

    suspend fun setUsername(name: String) {
        dataStore.edit { it[Keys.Username] = name.trim() }
    }

    suspend fun setThemePreference(preference: ThemePreference) {
        dataStore.edit { it[Keys.ThemePreference] = preference.storageValue }
    }

    /** Sets the ceiling, or clears it back to [NoBudgetIdr] when the user empties the field. */
    suspend fun setMonthlyBudgetIdr(amount: Long) {
        dataStore.edit { it[Keys.MonthlyBudgetIdr] = amount }
    }

    /**
     * The three writes onboarding finishes with, in one transaction.
     *
     * Separate writes would leave a window - brief, but real - in which onboarding reads as finished
     * while the name and the ceiling it collected have not landed, and the dashboard would greet the
     * user by the default name with nothing behind the progress bar.
     */
    suspend fun saveOnboardingProfile(username: String, monthlyBudgetIdr: Long) {
        dataStore.edit { preferences ->
            preferences[Keys.Username] = username.trim()
            preferences[Keys.MonthlyBudgetIdr] = monthlyBudgetIdr
            preferences[Keys.HasCompletedOnboarding] = true
        }
    }
}

private object Keys {
    val HasCompletedOnboarding = booleanPreferencesKey("has_completed_onboarding")
    val Username = stringPreferencesKey("username")
    val ThemePreference = stringPreferencesKey("theme_preference")
    val MonthlyBudgetIdr = longPreferencesKey("monthly_budget_idr")
}

private fun Preferences.toUserPreferences(): UserPreferences = UserPreferences(
    hasCompletedOnboarding = this[Keys.HasCompletedOnboarding] ?: false,
    username = this[Keys.Username] ?: "",
    themePreference = ThemePreference.fromStorage(this[Keys.ThemePreference]),
    monthlyBudgetIdr = this[Keys.MonthlyBudgetIdr] ?: NoBudgetIdr
)
