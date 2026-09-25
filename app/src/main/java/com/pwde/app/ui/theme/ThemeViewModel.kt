package com.pwde.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.prefs.UserSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/** Root-level ViewModel: feeds [PwdeTheme] at the Activity root. Null until settings first load. */
class ThemeViewModel(settingsRepository: SettingsRepository) : ViewModel() {
    val settings: StateFlow<UserSettings?> = settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
}
