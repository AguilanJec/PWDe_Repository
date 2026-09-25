package com.pwde.app.ui.navigation

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.pwde.app.data.model.Game
import com.pwde.app.data.model.GestureAction
import com.pwde.app.ui.common.FaceTrackingViewModel
import com.pwde.app.ui.common.pwdeViewModel
import com.pwde.app.ui.components.MainTab
import com.pwde.app.ui.controls.ChooseGestureScreen
import com.pwde.app.ui.controls.ChooseGestureViewModel
import com.pwde.app.ui.controls.ControlsDestination
import com.pwde.app.ui.controls.ControlsHubScreen
import com.pwde.app.ui.controls.CursorSpeedScreen
import com.pwde.app.ui.controls.CursorSpeedViewModel
import com.pwde.app.ui.controls.GesturesScreen
import com.pwde.app.ui.controls.GesturesViewModel
import com.pwde.app.ui.controls.InputModeScreen
import com.pwde.app.ui.controls.InputModeViewModel
import com.pwde.app.ui.controls.JoystickScreen
import com.pwde.app.ui.controls.JoystickViewModel
import com.pwde.app.ui.dashboard.DashboardDestination
import com.pwde.app.ui.dashboard.DashboardScreen
import com.pwde.app.ui.dashboard.DashboardViewModel
import com.pwde.app.ui.gabai.GabAiScreen
import com.pwde.app.ui.gabai.GabAiStart
import com.pwde.app.ui.gabai.GabAiViewModel
import com.pwde.app.ui.gameplay.GameplayViewModel
import com.pwde.app.ui.gameplay.PlayingScreen
import com.pwde.app.ui.games.FilterScreen
import com.pwde.app.ui.games.GameDetailScreen
import com.pwde.app.ui.games.GameDetailViewModel
import com.pwde.app.ui.games.GamesScreen
import com.pwde.app.ui.games.GamesViewModel
import com.pwde.app.ui.onboarding.AuthViewModel
import com.pwde.app.ui.onboarding.CreateAccountScreen
import com.pwde.app.ui.onboarding.ForgotPasswordScreen
import com.pwde.app.ui.onboarding.SignInScreen
import com.pwde.app.ui.onboarding.SplashScreen
import com.pwde.app.ui.onboarding.SplashViewModel
import com.pwde.app.ui.onboarding.WelcomeScreen
import com.pwde.app.ui.profile.ProfileScreen
import com.pwde.app.ui.profile.ProfileViewModel
import com.pwde.app.ui.setup.SetupScreen
import com.pwde.app.ui.setup.SetupViewModel
import com.pwde.app.ui.tutorial.WatchTutorialScreen
import com.pwde.app.ui.tutorial.WatchTutorialViewModel
import com.pwde.app.ui.voiceconfig.VoiceConfigScreen
import com.pwde.app.ui.voiceconfig.VoiceConfigViewModel
import com.pwde.app.ui.voice.LocalVoiceController
import com.pwde.app.ui.voice.VoiceNavigation
import com.pwde.app.ui.voice.VoiceViewModel
import com.pwde.app.ui.voicetutorial.VoiceTutorialScreen
import com.pwde.app.ui.voicetutorial.VoiceTutorialViewModel

/** The full A→H flow. Every destination is reachable and every screen has a way back. */
@Composable
fun PwdeNavHost(navController: NavHostController = rememberNavController()) {
    val activity = LocalActivity.current
    val screenReader = pwdeViewModel { ScreenReaderViewModel(it.settingsRepository, it.speechOutput) }
    val voice = pwdeViewModel {
        VoiceViewModel(it.voiceCommandManager, it.controlsRepository, it.settingsRepository, it.profileRepository)
    }
    // Observing voice state here keeps the recognizer running on every screen while PWDe is visible.
    voice.state.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) { screenReader.onScreenShown(Routes.spokenTitle(currentRoute)) }

    fun inMainApp() = runCatching { navController.getBackStackEntry(Routes.DASHBOARD) }.isSuccess

    // Standard voice commands. Voice "back" never closes the app from the root screen.
    LaunchedEffect(voice) {
        voice.navigation.collect { request ->
            when (request) {
                VoiceNavigation.BACK -> navController.popBackStack()
                VoiceNavigation.HOME -> if (inMainApp()) navController.popBackStack(Routes.DASHBOARD, inclusive = false)
                VoiceNavigation.SETTINGS -> if (inMainApp()) navController.navigate(Routes.CONTROLS) { launchSingleTop = true }
            }
        }
    }

    fun back() {
        if (!navController.popBackStack()) activity?.finish()
    }

    fun openTab(tab: MainTab) {
        val route = when (tab) {
            MainTab.PLAY -> Routes.DASHBOARD
            MainTab.GAMES -> Routes.GAMES
            MainTab.FILTER -> Routes.FILTER
            MainTab.PROFILE -> Routes.PROFILE
        }
        navController.navigate(route) {
            popUpTo(Routes.DASHBOARD) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    /** Leaves onboarding for good: Dashboard becomes the root. */
    fun enterMainApp() {
        navController.navigate(Routes.DASHBOARD) {
            popUpTo(navController.graph.id) { inclusive = true }
        }
    }

    /** After sign-in/guest choice: back to Profile if we came from there, else on to Setup. */
    fun afterAuthChoice() {
        val profileEntry = runCatching { navController.getBackStackEntry(Routes.PROFILE) }.getOrNull()
        if (profileEntry != null) navController.popBackStack(Routes.PROFILE, inclusive = false)
        else navController.navigate(Routes.setup())
    }

    CompositionLocalProvider(LocalVoiceController provides voice) {
    NavHost(navController, startDestination = Routes.SPLASH) {
        // A · Account
        composable(Routes.SPLASH) {
            SplashScreen(pwdeViewModel { SplashViewModel(it.settingsRepository, it.authRepository) }) { destination ->
                navController.navigate(destination) { popUpTo(Routes.SPLASH) { inclusive = true } }
            }
        }
        composable(Routes.WELCOME) {
            WelcomeScreen(
                onContinueAsGuest = { navController.navigate(Routes.setup()) },
                onHaveAccount = { navController.navigate(Routes.SIGN_IN) },
            )
        }
        composable(Routes.SIGN_IN) {
            SignInScreen(
                viewModel = pwdeViewModel { AuthViewModel(it.authRepository) },
                onBack = ::back,
                onSignedIn = ::afterAuthChoice,
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) },
                onCreateAccount = { navController.navigate(Routes.CREATE_ACCOUNT) },
                onUseAsGuest = ::afterAuthChoice,
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(pwdeViewModel { AuthViewModel(it.authRepository) }, onBack = ::back)
        }
        composable(Routes.CREATE_ACCOUNT) {
            CreateAccountScreen(
                viewModel = pwdeViewModel { AuthViewModel(it.authRepository) },
                onBack = ::back,
                onCreated = ::afterAuthChoice,
                onHaveAccount = ::back,
            )
        }

        // B · Setup, C · Voice tutorial
        composable(
            Routes.SETUP,
            arguments = listOf(navArgument("appearanceOnly") { type = NavType.BoolType; defaultValue = false }),
        ) { entry ->
            val appearanceOnly = entry.arguments?.getBoolean("appearanceOnly") ?: false
            SetupScreen(
                viewModel = pwdeViewModel(key = "setup-$appearanceOnly") { SetupViewModel(it.settingsRepository, appearanceOnly) },
                tryIt = pwdeViewModel(key = "setup-try-it") { FaceTrackingViewModel(it.faceTrackingManager) },
                onExit = ::back,
                onFinished = {
                    if (appearanceOnly) back() else navController.navigate(Routes.VOICE_TUTORIAL)
                },
            )
        }
        composable(Routes.VOICE_TUTORIAL) {
            VoiceTutorialScreen(
                viewModel = pwdeViewModel {
                    VoiceTutorialViewModel(it.settingsRepository, it.speechOutput, it.isSystemScreenReaderOn())
                },
                onExit = ::back,
                onFinished = ::enterMainApp,
            )
        }

        // D · Play
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = pwdeViewModel {
                    DashboardViewModel(
                        it.settingsRepository, it.authRepository, it.faceTrackingManager, it.voiceCommandManager, it.gabAiRepository,
                    )
                },
                onNavigate = { destination ->
                    when (destination) {
                        DashboardDestination.CONTROLS -> navController.navigate(Routes.CONTROLS)
                        DashboardDestination.VOICE -> navController.navigate(Routes.VOICE_CONFIG)
                        // Release builds have no such destination at all (see src/release).
                        DashboardDestination.TESTING_STATION -> if (TESTING_STATION_AVAILABLE) navController.navigate(Routes.TESTING_STATION)
                        DashboardDestination.WATCH_TUTORIAL -> navController.navigate(Routes.WATCH_TUTORIAL)
                        DashboardDestination.GABAI -> navController.navigate(Routes.gabai())
                        DashboardDestination.START_PLAYING -> openTab(MainTab.GAMES)
                    }
                },
                onTab = ::openTab,
            )
        }
        composable(Routes.GAMES) {
            GamesScreen(
                viewModel = pwdeViewModel { GamesViewModel(it.profileRepository) },
                onGame = { navController.navigate(Routes.gameDetail(it.id)) },
                onTab = ::openTab,
            )
        }
        composable(Routes.GAME_DETAIL, arguments = listOf(navArgument("gameId") { type = NavType.StringType })) { entry ->
            val game = Game.byId(entry.arguments?.getString("gameId"))
            if (game == null) {
                LaunchedEffect(Unit) { back() }
            } else {
                GameDetailScreen(
                    game = game,
                    viewModel = pwdeViewModel(key = "game-${game.id}") { GameDetailViewModel(it.profileRepository, game) },
                    onBack = ::back,
                    onPlay = { profileId -> navController.navigate(Routes.playing(game.id, profileId)) },
                    onEditProfile = { navController.navigate(Routes.gabai(editProfileId = it)) },
                    onSetUpWithGabAi = { navController.navigate(Routes.gabai(newGameProfile = true, gameId = game.id)) },
                )
            }
        }
        composable(
            Routes.PLAYING,
            arguments = listOf(
                navArgument("gameId") { type = NavType.StringType },
                navArgument("profile") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val game = Game.byId(entry.arguments?.getString("gameId"))
            val profileId = entry.arguments?.getLong("profile")?.takeIf { it >= 0 }
            PlayingScreen(
                viewModel = pwdeViewModel {
                    GameplayViewModel(
                        it.faceTrackingManager, it.inGameVoiceEngine, it.controlsRepository, it.profileRepository,
                        it.settingsRepository, it.gabAiRepository, game, profileId,
                    )
                },
                onExit = ::back,
            )
        }
        composable(Routes.FILTER) {
            FilterScreen(
                viewModel = pwdeViewModel { GamesViewModel(it.profileRepository) },
                onGame = { navController.navigate(Routes.gameDetail(it.id)) },
                onTab = ::openTab,
            )
        }

        // E · Controls
        composable(Routes.CONTROLS) {
            ControlsHubScreen(onBack = ::back) { destination ->
                navController.navigate(
                    when (destination) {
                        ControlsDestination.INPUT -> Routes.CONTROLS_INPUT
                        ControlsDestination.GESTURES -> Routes.CONTROLS_GESTURES
                        ControlsDestination.CURSOR -> Routes.CONTROLS_CURSOR
                        ControlsDestination.JOYSTICK -> Routes.CONTROLS_JOYSTICK
                        ControlsDestination.VOICE -> Routes.VOICE_CONFIG
                        ControlsDestination.CUSTOM_BUTTONS -> Routes.gabai(newGameProfile = true)
                    },
                )
            }
        }
        composable(Routes.CONTROLS_INPUT) {
            InputModeScreen(pwdeViewModel { InputModeViewModel(it.settingsRepository) }, onBack = ::back)
        }
        composable(Routes.CONTROLS_GESTURES) {
            GesturesScreen(
                viewModel = pwdeViewModel { GesturesViewModel(it.controlsRepository) },
                onBack = ::back,
                onChoose = { navController.navigate(Routes.chooseGesture(it.name)) },
            )
        }
        composable(Routes.CHOOSE_GESTURE, arguments = listOf(navArgument("action") { type = NavType.StringType })) { entry ->
            val action = GestureAction.entries.firstOrNull { it.name == entry.arguments?.getString("action") }
            if (action == null) {
                LaunchedEffect(Unit) { back() }
            } else {
                ChooseGestureScreen(
                    viewModel = pwdeViewModel(key = "gesture-${action.name}") {
                        ChooseGestureViewModel(it.controlsRepository, it.faceTrackingManager, action)
                    },
                    onBack = ::back,
                )
            }
        }
        composable(Routes.CONTROLS_CURSOR) {
            CursorSpeedScreen(pwdeViewModel { CursorSpeedViewModel(it.controlsRepository, it.faceTrackingManager) }, onBack = ::back)
        }
        composable(Routes.CONTROLS_JOYSTICK) {
            JoystickScreen(pwdeViewModel { JoystickViewModel(it.controlsRepository, it.faceTrackingManager) }, onBack = ::back)
        }
        composable(Routes.VOICE_CONFIG) {
            VoiceConfigScreen(pwdeViewModel { VoiceConfigViewModel(it.controlsRepository, it.voiceCommandManager) }, onBack = ::back)
        }

        // F · Testing Station (debug builds only — see debug/ and release/ source sets), tutorial video
        debugDestinations(onBack = ::back)
        composable(Routes.WATCH_TUTORIAL) {
            WatchTutorialScreen(pwdeViewModel { WatchTutorialViewModel(it.newTutorialPlayer()) }, onBack = ::back)
        }

        // G · GabAI
        composable(
            Routes.GABAI,
            arguments = listOf(
                navArgument("start") { type = NavType.StringType; defaultValue = Routes.GABAI_START_WELCOME },
                navArgument("game") { type = NavType.StringType; defaultValue = "" },
                navArgument("edit") { type = NavType.LongType; defaultValue = -1L },
            ),
        ) { entry ->
            val args = entry.arguments
            val editId = args?.getLong("edit")?.takeIf { it >= 0 }
            val start = when {
                editId != null -> GabAiStart.EditGameProfile(editId)
                args?.getString("start") == Routes.GABAI_START_GAME -> GabAiStart.NewGameProfile(args.getString("game")?.ifEmpty { null })
                else -> GabAiStart.Welcome
            }
            GabAiScreen(
                viewModel = pwdeViewModel(key = "gabai-$start") {
                    GabAiViewModel(
                        it.gabAiRepository, it.profileRepository, it.controlsRepository, it.settingsRepository,
                        it.voiceCommandManager, it.faceTrackingManager, start,
                    )
                },
                onExit = ::back,
                onDashboard = { if (!navController.popBackStack(Routes.DASHBOARD, inclusive = false)) enterMainApp() },
                onPlay = { gameId, profileId ->
                    navController.navigate(Routes.playing(gameId, profileId)) {
                        popUpTo(Routes.DASHBOARD) { inclusive = false }
                    }
                },
            )
        }

        // H · Profile
        composable(Routes.PROFILE) {
            ProfileScreen(
                viewModel = pwdeViewModel {
                    ProfileViewModel(it.authRepository, it.syncRepository, it.profileRepository, it.controlsRepository, it.settingsRepository)
                },
                onSignIn = { navController.navigate(Routes.SIGN_IN) },
                onEditGameProfile = { navController.navigate(Routes.gabai(editProfileId = it)) },
                onPlayGameProfile = { gameId, profileId -> navController.navigate(Routes.playing(gameId, profileId)) },
                onNewWithGabAi = { navController.navigate(Routes.gabai()) },
                onEditAppearance = { navController.navigate(Routes.setup(appearanceOnly = true)) },
                onControls = { navController.navigate(Routes.CONTROLS) },
                onTab = ::openTab,
            )
        }
    }
    }
}
