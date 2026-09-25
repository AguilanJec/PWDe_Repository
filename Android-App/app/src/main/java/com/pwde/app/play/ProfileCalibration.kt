package com.pwde.app.play

import com.pwde.app.data.local.CalibrationProfile
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.GameProfile
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.inputModeOrDefault
import com.pwde.app.data.prefs.SettingsRepository
import kotlinx.coroutines.flow.first

/** Play with the calibration [profile] was made with: applies it and its input mode. Null if it has none. */
suspend fun applyProfileCalibration(
    profile: GameProfile,
    profileRepository: ProfileRepository,
    controlsRepository: ControlsRepository,
    settingsRepository: SettingsRepository,
): CalibrationProfile? {
    val calibration = profile.calibrationProfileId?.let { profileRepository.getCalibrationProfile(it) } ?: return null
    controlsRepository.applyCalibration(calibration)
    if (settingsRepository.settings.first().inputMode != calibration.inputModeOrDefault) {
        settingsRepository.setInputMode(calibration.inputModeOrDefault)
    }
    return calibration
}
