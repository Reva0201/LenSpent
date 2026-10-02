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
import org.umn.lensspent.AnalyticsScreen
import org.umn.lensspent.CameraScreen
import org.umn.lensspent.ExpenseReviewScreen
import org.umn.lensspent.HomeScreen
import org.umn.lensspent.ui.onboarding.OnboardingScreen
import org.umn.lensspent.ui.settings.SettingsScreen

/** The capture a review screen was opened for, as it appears in the route. */
private const val CaptureIdArg = "captureId"

/**
 * Every destination in the app, named once.
 *
 * The routes used to be string literals spread across the graph, which is how a `popBackStack("home")`
 * quietly rots the day someone renames a screen. Naming them here means the compiler has something to
 * check, and the argument-taking route has exactly one builder for its one shape.
 */
object AppRoutes {
    const val Onboarding = "onboarding"
    const val Home = "home"
    const val Camera = "camera"
    const val Analytics = "analytics"
    const val AiInsights = "ai-insights"
    const val Settings = "settings"
    const val Review = "review/{$CaptureIdArg}"

    fun review(captureId: Long): String = "review/$captureId"
}

/**
 * The navigation graph: onboarding and the dashboard at the top, everything else pushed onto it.
 *
 * The two arguments are the answers the root has already waited for, not state this composable reads
 * again - keeping "which screen comes first" a single decision made in one place.
 */
@Composable
fun LensSpentNavHost(
    hasCompletedOnboarding: Boolean,
    username: String,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()

    // Read once, on the first composition. The flag flips the instant onboarding writes it, and a start
    // destination that changed underneath the user would rebuild the graph and reset the back stack to
    // a screen they had already left.
    val startDestination = remember {
        if (hasCompletedOnboarding) AppRoutes.Home else AppRoutes.Onboarding
    }

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
                onNavigateToSettings = { navController.navigate(AppRoutes.Settings) }
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
