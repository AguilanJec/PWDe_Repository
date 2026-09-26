package com.pwde.app.ui.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.pwde.app.ui.common.pwdeViewModel
import com.pwde.app.ui.eyetracking.EyeTrackingScreen
import com.pwde.app.ui.eyetracking.EyeTrackingViewModel
import com.pwde.app.ui.testingstation.TestingStationScreen
import com.pwde.app.ui.testingstation.TestingStationViewModel

/**
 * Debug builds only. The release source set has a no-op twin, so the Testing Station's code and
 * route are not compiled into release builds at all.
 */
const val TESTING_STATION_AVAILABLE = true

fun NavGraphBuilder.debugDestinations(onBack: () -> Unit) {
    composable(Routes.TESTING_STATION) {
        TestingStationScreen(
            viewModel = pwdeViewModel {
                TestingStationViewModel(
                    it.faceTrackingManager,
                    it.voiceCommandManager,
                    it.wakeWordEngine,
                    it.wakeWordTuningStore,
                    it.buttonOverlayPrefs,
                )
            },
            onBack = onBack,
        )
    }
    // The gaze SDK diagnostics rig is a development tool: it reports raw SDK frames, and in release
    // builds the user has the calibration screen instead.
    composable(Routes.EYE_TRACKING) {
        EyeTrackingScreen(
            viewModel = pwdeViewModel { EyeTrackingViewModel(it.eyeControlManager) },
            onBack = onBack,
        )
    }
}
