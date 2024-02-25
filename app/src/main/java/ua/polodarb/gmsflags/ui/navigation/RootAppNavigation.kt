@file:OptIn(ExperimentalAnimationApi::class)

package ua.polodarb.gmsflags.ui.navigation

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalUriHandler
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject
import ua.polodarb.gmsflags.GMSApplication
import ua.polodarb.gmsflags.R
import ua.polodarb.gmsflags.ui.MainActivity
import ua.polodarb.gmsflags.ui.animations.enterAnim
import ua.polodarb.gmsflags.ui.animations.exitAnim
import ua.polodarb.gmsflags.ui.screens.RootScreen
import ua.polodarb.gmsflags.ui.screens.firstStart.RequestNotificationPermissionScreen
import ua.polodarb.gmsflags.ui.screens.firstStart.RootRequestScreen
import ua.polodarb.gmsflags.ui.screens.firstStart.WelcomeScreen
import ua.polodarb.gmsflags.ui.screens.flagChange.FlagChangeScreen
import ua.polodarb.gmsflags.ui.screens.flagChange.extScreens.AddFlagList
import ua.polodarb.gmsflags.ui.screens.loadFile.LoadFileScreen
import ua.polodarb.gmsflags.ui.screens.settings.SettingsScreen
import ua.polodarb.gmsflags.ui.screens.settings.screens.about.AboutScreen
import ua.polodarb.gmsflags.ui.screens.settings.screens.resetFlags.ResetFlagsScreen
import ua.polodarb.gmsflags.ui.screens.settings.screens.resetSaved.ResetSavedScreen
import ua.polodarb.gmsflags.ui.screens.settings.screens.startRoute.ChangeNavigationScreen

@Composable
internal fun RootAppNavigation(
    modifier: Modifier = Modifier,
    isFirstStart: Boolean,
    loadFlagIntent: Intent?,
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = if (isFirstStart) {
            ScreensDestination.Welcome.screenRoute
        } else if (isLoadFileIntent(loadFlagIntent)) {
            ScreensDestination.LoadFile.screenRoute
        } else {
            ScreensDestination.Root.screenRoute
        },
        modifier = modifier
    ) {
        rootComposable(
            navController = navController,
            isFirstStart = isFirstStart,
            loadFlagIntent = loadFlagIntent
        )
        welcomeComposable(navController = navController)
        rootRequestComposable(navController = navController)
        notificationRequestComposable(navController = navController)
        flagChangeComposable(navController = navController)
        addFlagListComposable(navController = navController)
        settingsComposable(navController = navController)
        settingsResetFlagsComposable(navController = navController)
        settingsResetSavedComposable(navController = navController)
        settingsChangeNavigationComposable(navController = navController)
        settingsAboutComposable(navController = navController)
    }
}

@OptIn(ExperimentalAnimationApi::class)
private fun NavGraphBuilder.rootComposable(
    navController: NavHostController,
    isFirstStart: Boolean,
    loadFlagIntent: Intent?
) {
    if (!isFirstStart && isLoadFileIntent(loadFlagIntent)) {
        composable(
            route = ScreensDestination.LoadFile.screenRoute,
            enterTransition = { enterAnim(toLeft = true) },
            exitTransition = { exitAnim(toLeft = true) },
            popEnterTransition = { enterAnim(toLeft = false) },
            popExitTransition = { exitAnim(toLeft = false) }
        ) {
            LoadFileScreen(loadFlagIntent?.data)
        }
    } else {
        composable(
            route = ScreensDestination.Root.screenRoute,
            enterTransition = { enterAnim(toLeft = true) },
            exitTransition = { exitAnim(toLeft = true) },
            popEnterTransition = { enterAnim(toLeft = false) },
            popExitTransition = { exitAnim(toLeft = false) }
        ) {
            RootScreen(isFirstStart = isFirstStart, parentNavController = navController)
        }
    }
}


private fun NavGraphBuilder.welcomeComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.Welcome.screenRoute,
        enterTransition = { enterAnim(toLeft = false) },
        exitTransition = { exitAnim(toLeft = true) },
    ) {
        val uriHandler = LocalUriHandler.current
        WelcomeScreen(
            onStart = {
                navController.navigate(ScreensDestination.RootRequest.screenRoute)
            },
            openLink = {
                uriHandler.openUri(it)
            }
        )
    }
}

private fun NavGraphBuilder.rootRequestComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.RootRequest.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = true) },
        popEnterTransition = { enterAnim(toLeft = false) },
        popExitTransition = { exitAnim(toLeft = false) }
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        val mainActivity = LocalContext.current as MainActivity
        val gmsApplication = koinInject<GMSApplication>()
        var isButtonLoading by rememberSaveable { mutableStateOf(false) }

        RootRequestScreen(
            onExit = { mainActivity.finish() },
            onRootRequest = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                isButtonLoading = true

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        gmsApplication.initShell()
                    } catch (_: Exception) {
                    }

                    CoroutineScope(Dispatchers.IO).launch {
                        if (Shell.getShell().isRoot) {
                            withContext(Dispatchers.Main) {
                                gmsApplication.initDB()
                                delay(700)
                                navController.navigate(ScreensDestination.NotificationRequest.screenRoute)
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                delay(150)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                                isButtonLoading = false
                                Toast.makeText(gmsApplication, "ROOT IS DENIED!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }

                }
            },
            isButtonLoading = isButtonLoading
        )
    }
}

private fun NavGraphBuilder.notificationRequestComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.NotificationRequest.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = true) },
        popEnterTransition = { enterAnim(toLeft = false) },
        popExitTransition = { exitAnim(toLeft = false) }
    ) {
        val hapticFeedback = LocalHapticFeedback.current
        val mainActivity = LocalContext.current as MainActivity

        RequestNotificationPermissionScreen(
            onSkip = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                mainActivity.setFirstLaunch()
                Toast.makeText(mainActivity, R.string.notifications_toast, Toast.LENGTH_SHORT)
                    .show()
                navController.navigate(ScreensDestination.Root.screenRoute)
            },
            onNotificationRequest = {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                mainActivity.setFirstLaunch()
                navController.navigate(ScreensDestination.Root.screenRoute)
            }
        )
    }
}

private fun NavGraphBuilder.flagChangeComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.FlagChange.createStringRoute(ScreensDestination.Packages.screenRoute),
        arguments = listOf(navArgument("flagChange") { type = NavType.StringType }),
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = true) },
        popEnterTransition = { enterAnim(toLeft = false) },
        popExitTransition = { exitAnim(toLeft = false) }
    ) { backStackEntry ->
        FlagChangeScreen(
            onBackPressed = navController::navigateUp,
            packageName = Uri.decode(backStackEntry.arguments?.getString("flagChange")),
            onAddMultipleFlags = {
                navController.navigate(
                    ScreensDestination.AddFlagList.createRoute(Uri.encode(it))
                )
            },
        )
    }
}

private fun NavGraphBuilder.addFlagListComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.AddFlagList.createStringRoute(ScreensDestination.FlagChange.screenRoute),
        arguments = listOf(navArgument("addFlagList") { type = NavType.StringType }),
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = true) },
        popEnterTransition = { enterAnim(toLeft = false) },
        popExitTransition = { exitAnim(toLeft = false) }
    ) { backStackEntry ->
        AddFlagList(
            onBackPressed = navController::navigateUp,
            packageName = Uri.decode(backStackEntry.arguments?.getString("addFlagList"))
        )
    }
}

private fun NavGraphBuilder.settingsComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.Settings.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = true) },
        popEnterTransition = { enterAnim(toLeft = false) },
        popExitTransition = { exitAnim(toLeft = false) }
    ) {
        SettingsScreen(
            onBackPressed = navController::navigateUp,
            onResetFlagsClick = {
                navController.navigate(ScreensDestination.SettingsResetFlags.screenRoute)
            },
            onResetSavedClick = {
                navController.navigate(ScreensDestination.SettingsResetSaved.screenRoute)
            },
            onChangeNavigationClick = {
                navController.navigate(ScreensDestination.SettingsChangeNavigation.screenRoute)
            },
            onAboutClick = {
                navController.navigate(ScreensDestination.SettingsAbout.screenRoute)
            }
        )
    }
}

private fun NavGraphBuilder.settingsResetFlagsComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.SettingsResetFlags.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = false) },
    ) {
        ResetFlagsScreen(
            onBackPressed = navController::navigateUp
        )
    }
}

private fun NavGraphBuilder.settingsResetSavedComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.SettingsResetSaved.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = false) },
    ) {
        ResetSavedScreen(
            onBackPressed = navController::navigateUp
        )
    }
}

private fun NavGraphBuilder.settingsChangeNavigationComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.SettingsChangeNavigation.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = false) },
    ) {
        ChangeNavigationScreen(
            onBackPressed = navController::navigateUp
        )
    }
}

private fun NavGraphBuilder.settingsAboutComposable(navController: NavHostController) {
    composable(
        route = ScreensDestination.SettingsAbout.screenRoute,
        enterTransition = { enterAnim(toLeft = true) },
        exitTransition = { exitAnim(toLeft = false) },
    ) {
        AboutScreen(
            onBackPressed = navController::navigateUp
        )
    }
}

fun isLoadFileIntent(intent: Intent?): Boolean {
    return intent != null && intent.action == Intent.ACTION_VIEW && intent.type == "application/xml"
}