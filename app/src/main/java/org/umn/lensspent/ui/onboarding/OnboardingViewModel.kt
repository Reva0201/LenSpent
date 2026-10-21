package org.umn.lensspent.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.umn.lensspent.data.local.NoBudgetIdr
import org.umn.lensspent.data.local.UserPreferencesRepository
import org.umn.lensspent.ui.parseBudgetInput

/** Which of the two pages is on screen. */
enum class OnboardingStep {
    Agreement,
    Profile
}

/**
 * The onboarding pages and nothing else: what to draw, and what the user has typed so far.
 *
 * There is no saved copy of the stored values here. Onboarding writes them once and never reads them
 * back, so a flow subscription would only be a way to re-read what this screen itself just typed.
 */
data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.Agreement,
    val username: String = "",
    /**
     * The monthly budget exactly as typed.
     *
     * Text rather than an amount, because a half-typed number is still what the field has to show: a
     * field that stored the parsed value would erase the digits under the cursor the moment they were
     * not a number. [monthlyBudgetIdr] is the reading of this text.
     */
    val budgetText: String = "",
    /**
     * Set once the profile write returns.
     *
     * The write, not the tap, is what ends onboarding: if the button fired a navigation callback
     * directly, a write that failed would leave the user on the dashboard with onboarding marked
     * unfinished, and the next launch would put them back at the welcome page with their name intact.
     */
    val isFinished: Boolean = false
) {
    /**
     * The typed budget in whole Rupiah, or null when the text is not a number at all.
     *
     * Blank reads as [NoBudgetIdr]: the field is optional, and the button waits only on text it would
     * have to throw away.
     */
    val monthlyBudgetIdr: Long? get() = parseBudgetInput(budgetText)
}

/**
 * Drives the first launch.
 *
 * Onboarding is the one screen that cannot skip the store: it exists to fill it, so it talks to the
 * repository directly rather than through a loaded state that would only ever be its own input.
 */
class OnboardingViewModel(private val preferences: UserPreferencesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    /** The disclosure page was read and accepted; the profile page follows it. */
    fun onAgree() = _uiState.update { it.copy(step = OnboardingStep.Profile) }

    /** Back to the disclosure, with the typed name kept: backing up is not meant to cost anything. */
    fun onBackToAgreement() = _uiState.update { it.copy(step = OnboardingStep.Agreement) }

    fun onUsernameChange(username: String) = _uiState.update { it.copy(username = username) }

    fun onBudgetChange(budgetText: String) = _uiState.update { it.copy(budgetText = budgetText) }

    /**
     * Saves the name, the ceiling and the flag that ends onboarding, in one transaction.
     *
     * A blank name is stored as blank on purpose: the name is an honour, not a requirement, and the
     * dashboard has a fallback greeting for it. Refusing to continue without one would lock a
     * privacy-minded user out of the app they installed to track their spending, and the budget is the
     * same kind of answer - [NoBudgetIdr] is the user saying they would rather not commit to a ceiling.
     * Unreadable text is the one case that does nothing at all, so a number they did type is never
     * quietly dropped.
     */
    fun startTracking() {
        if (_uiState.value.isFinished) return
        val budget = _uiState.value.monthlyBudgetIdr ?: return
        viewModelScope.launch {
            preferences.saveOnboardingProfile(
                username = _uiState.value.username,
                monthlyBudgetIdr = budget
            )
            _uiState.update { it.copy(isFinished = true) }
        }
    }
}
