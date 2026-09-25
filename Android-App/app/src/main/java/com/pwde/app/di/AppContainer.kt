package com.pwde.app.di

import android.content.Context
import android.view.accessibility.AccessibilityManager
import androidx.datastore.preferences.preferencesDataStore
import com.pwde.app.BuildConfig
import com.pwde.app.data.gabai.CloudHudDetector
import com.pwde.app.data.gabai.GabAiRepository
import com.pwde.app.data.gabai.HudDetector
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
import com.pwde.app.play.LivePlay
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.MediaPipeFaceTrackingManager
import com.pwde.app.sensors.voice.AndroidVoiceCommandManager
import com.pwde.app.sensors.voice.InGameVoiceEngine
import com.pwde.app.sensors.voice.MicArbiter
import com.pwde.app.sensors.voice.SpeechRecognizerInGameVoiceEngine
import com.pwde.app.sensors.voice.VoiceCommandManager

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

    /** Camera + MediaPipe head/face tracking (motion-sensor demo mode when the camera can't be used). */
    val faceTrackingManager: FaceTrackingManager by lazy {
        MediaPipeFaceTrackingManager(context, controlsRepository, settingsRepository)
    }

    /** Makes sure gameplay's voice engine and the app-wide one never listen at the same time. */
    private val micArbiter by lazy { MicArbiter() }

    /** App-scoped voice commands (Android SpeechRecognizer, typed fallback). Used everywhere except gameplay. */
    val voiceCommandManager: VoiceCommandManager by lazy {
        AndroidVoiceCommandManager(context, controlsRepository, settingsRepository, micArbiter)
    }

    /** Gameplay-time voice recognition, scoped to the active game profile's commands. */
    val inGameVoiceEngine: InGameVoiceEngine by lazy {
        // swap SpeechRecognizerInGameVoiceEngine for a dedicated low-latency engine here once one is chosen — GameplayViewModel and everything above it needs no changes
        SpeechRecognizerInGameVoiceEngine(context, controlsRepository, micArbiter)
    }

    /** Resumable GabAI sessions and game screenshots. */
    val gabAiRepository by lazy { GabAiRepository(context, database.gabAiSessionDao()) }

    /** Auto-detects HUD buttons on GabAI screenshots; off unless pwde.detection.url is set. */
    val hudDetector: HudDetector by lazy {
        BuildConfig.DETECTION_URL.takeIf { it.isNotBlank() }?.let(::CloudHudDetector) ?: HudDetector.None
    }

    /** The live session over the real game, shared by PlayService, the accessibility service and the UI. */
    val livePlay by lazy { LivePlay() }

    fun newTutorialPlayer() = TutorialPlayer(context)

    /** True when TalkBack (or another touch-exploration screen reader) is running. */
    fun isSystemScreenReaderOn(): Boolean {
        val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        return manager?.isTouchExplorationEnabled == true
    }
}
