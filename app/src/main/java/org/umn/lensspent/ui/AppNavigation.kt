package org.umn.lensspent.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.umn.lensspent.AiChatScreen
import org.umn.lensspent.AddExpenseScreen
import org.umn.lensspent.AnalyticsScreen
import org.umn.lensspent.CameraScreen
import org.umn.lensspent.ExpenseReviewScreen
import org.umn.lensspent.HomeScreen
import org.umn.lensspent.TransactionDetailScreen
import org.umn.lensspent.data.local.UserPreferences
import org.umn.lensspent.ui.onboarding.OnboardingScreen
import org.umn.lensspent.ui.settings.SettingsScreen

/**
 * The capture a review screen was opened for, as it appears in the route.
 */
private const val CaptureIdArg = "captureId"

/** The expense a detail screen was opened for, as it appears in the route. */
private const val TransactionIdArg = "transactionId"

/**
 * Every destination in the app, named once.
 *
 * The routes used to be string literals spread across the graph, which is how a `popBackStack("home")`
 * quietly rots the day someone renames a screen. Naming them here means the compiler has something to
 * check, and the argument-taking routes have exactly one builder for their one shape.
 */
object AppRoutes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Camera = "camera"
    const val Analytics = "analytics"
    const val AiInsights = "ai-insights"
    const val Settings = "settings"
    const val AddExpense = "add-expense"
    const val Review = "review/{$CaptureIdArg}"
    const val Transaction = "transaction/{$TransactionIdArg}"

    fun review(captureId: Long): String = "review/$captureId"

    fun transaction(transactionId: Long): String = "transaction/$transactionId"
}

/**
 * The screen a launch opens at, or null while the stored answers have not arrived.
 *
 * The null is the whole point of this function. A DataStore read is asynchronous, so until it lands
 * the app is holding what a *fresh install* holds - onboarding not finished, no name - because that is
 * what "nothing stored yet" looks like from here. Deciding against those defaults is how a returning
 * user was walked through the welcome pages again on every cold start, with their name and their theme
 * still on disk. So the question is answered only once there is something to answer it with, and the
 * null means "not yet" rather than "no".
 */
internal fun launchRoute(stored: UserPreferences?): String? = when {
    stored == null -> null
    stored.hasCompletedOnboarding -> AppRoutes.Home
    else -> AppRoutes.Onboarding
}

/**
 * The navigation graph: onboarding and the dashboard at the top, everything else pushed onto it.
 *
 * The two arguments are the answers the root has already waited for, not state this composable reads
 * again - keeping "which screen comes first" a single decision made in one place. [startDestination] is
 * that decision already made: this composable is not given a flag it might have to interpret before the
 * store has answered, because interpreting it too early is exactly the bug [launchRoute] prevents.
 */
@Composable
fun LensSpentNavHost(
    startDestination: String,
    username: String,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(AppRoutes.Onboarding) {
            OnboardingScreen(
                // Onboarding is the bottom of the stack, so finishing it replaces that entry instead of
                // stacking the dashboard on a page the user has no way back to.
                onFinished = {
                    navController.navigate(AppRoutes.Home) {
                        popUpTo(AppRoutes.Onboarding) { inclusive = true }
                    }
                }
            )
        }

        composable(AppRoutes.Home) {
            HomeScreen(
                username = username,
                onNavigateToCamera = { navController.navigate(AppRoutes.Camera) },
                onNavigateToAnalytics = { navController.navigate(AppRoutes.Analytics) },
                onNavigateToAiInsights = { navController.navigate(AppRoutes.AiInsights) },
                onNavigateToSettings = { navController.navigate(AppRoutes.Settings) },
                onNavigateToAddExpense = { navController.navigate(AppRoutes.AddExpense) },
                onOpenTransaction = { transactionId ->
                    navController.navigate(AppRoutes.transaction(transactionId))
                }
            )
        }

        composable(AppRoutes.AddExpense) {
            AddExpenseScreen(
                onBack = { navController.popBackStack() },
                // Home rather than a pop, so the row that was just written is on screen instead of the
                // dashboard's position in the stack being whatever happened to be beneath this form.
                onSaved = { navController.popBackStack(route = AppRoutes.Home, inclusive = false) }
            )
        }

        composable(
            route = AppRoutes.Transaction,
            arguments = listOf(navArgument(TransactionIdArg) { type = NavType.LongType })
        ) { entry ->
            val transactionId = entry.arguments?.getLong(TransactionIdArg) ?: return@composable
            TransactionDetailScreen(
                transactionId = transactionId,
                onBack = { navController.popBackStack() },
                onNavigateToHome = {
                    navController.popBackStack(route = AppRoutes.Home, inclusive = false)
                }
            )
        }

        composable(AppRoutes.Camera) {
            CameraScreen(
                onBack = { navController.popBackStack() },
                onNavigateToReview = { captureId -> navController.navigate(AppRoutes.review(captureId)) }
            )
        }

        composable(
            route = AppRoutes.Review,
            arguments = listOf(navArgument(CaptureIdArg) { type = NavType.LongType })
        ) { entry ->
            val captureId = entry.arguments?.getLong(CaptureIdArg) ?: return@composable
            ExpenseReviewScreen(
                captureId = captureId,
                onFinished = {
                    // Land on the dashboard, past the camera, so the saved row is visible.
                    navController.popBackStack(route = AppRoutes.Home, inclusive = false)
                }
            )
        }

        composable(AppRoutes.Analytics) {
            AnalyticsScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppRoutes.AiInsights) {
            AiChatScreen(onNavigateBack = { navController.popBackStack() })
        }

        composable(AppRoutes.Settings) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
