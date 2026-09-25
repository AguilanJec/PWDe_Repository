package com.pwde.app.di

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.datastore.preferences.preferencesDataStore
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.ProfileRepository
import com.pwde.app.data.local.PwdeDatabase
import com.pwde.app.data.media.TutorialPlayer
import com.pwde.app.data.prefs.DataStoreSettingsRepository
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.data.remote.AuthRepository
import com.pwde.app.data.remote.NoOpSyncRepository
import com.pwde.app.data.remote.SyncRepository
import com.pwde.app.data.speech.SpeechOutput

private val Context.settingsDataStore by preferencesDataStore(name = "user_settings")

/** Manual DI: app-wide singletons, created lazily. Lives on [com.pwde.app.PwdeApplication]. */
class AppContainer(private val context: Context) {
    private val database by lazy { PwdeDatabase.create(context) }

    val settingsRepository: SettingsRepository by lazy { DataStoreSettingsRepository(context.settingsDataStore) }
    val profileRepository by lazy {
        ProfileRepository(database.calibrationProfileDao(), database.gameProfileDao())
    }
    val controlsRepository by lazy { ControlsRepository(database.controlSettingsDao()) }
    val authRepository: AuthRepository by lazy { AuthRepository.create(context) }
    val syncRepository: SyncRepository by lazy { NoOpSyncRepository(authRepository) }
    val speechOutput by lazy { SpeechOutput(context) }

    fun newTutorialPlayer() = TutorialPlayer(context)

    /** True when TalkBack (or another touch-exploration screen reader) is running. */
    fun isSystemScreenReaderOn(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }
}
