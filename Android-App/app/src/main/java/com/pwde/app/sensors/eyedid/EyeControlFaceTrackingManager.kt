package com.pwde.app.sensors.eyedid

import androidx.camera.core.SurfaceRequest
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.prefs.InputMode
import com.pwde.app.data.prefs.SettingsRepository
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.sensors.face.GazeDwell
import com.pwde.app.sensors.face.TrackingStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn

/**
 * The one [FaceTrackingManager] the app depends on. There are two engines behind it and only one
 * front camera between them, so selecting an input mode *is* the handover:
 *
 * - [InputMode.EYE] routes everything to the SeeSo/Eyedid gaze engine ([eyes]);
 * - every other mode routes to MediaPipe head/face tracking ([camera]).
 *
 * Nothing here tells an engine to stop. The engines are both driven by collection, so switching
 * mode cancels one branch of the [flatMapLatest] and starts the other — the cancelled branch is what
 * releases its camera. For the gaze engine that release is explicit ([EyedidFaceTrackingManager.release]),
 * because the SDK is callback-driven and cannot notice that nobody is listening any more.
 *
 * ## What eye mode gives up
 * The gaze SDK reports no face mesh, so in eye mode [gestureEvents] is deliberately empty: facial
 * gestures are unavailable rather than silently doing nothing, and [captureJoystickCenter] is false
 * because there is no head pose to make a joystick neutral from.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class EyeControlFaceTrackingManager(
    private val camera: FaceTrackingManager,
    /** Exposed so the calibration UI can drive the engine the control pipeline will actually use. */
    val eyes: EyedidFaceTrackingManager,
    settingsRepository: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : FaceTrackingManager {

    /**
     * Both the mode and the master switch, because either one means the gaze engine should let go of
     * the camera. Not a plain `inputMode == EYE` check: with PWDe switched off, tracking must stop
     * whatever mode is selected.
     */
    private val eyeMode: StateFlow<Boolean> = settingsRepository.settings
        .map { it.inputMode == InputMode.EYE && it.pwdeEnabled }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, false)

    private val route: Flow<Boolean> = settingsRepository.settings
        .map { it.inputMode == InputMode.EYE && it.pwdeEnabled }
        .distinctUntilChanged()

    override val state: StateFlow<FaceState> = route
        .flatMapLatest { eyeControl ->
            when {
                eyeControl -> eyes.state
                    .onStart { eyes.acquire(EYE_HOLDER) }
                    // Runs on cancellation too, which is exactly the handover: the mode changed, so
                    // the gaze engine must let go of the camera for the head engine to take it.
                    .onCompletion { eyes.release(EYE_HOLDER) }
                else -> camera.state
            }
        }
        .stateIn(scope, SharingStarted.WhileSubscribed(0), FaceState(status = TrackingStatus.Idle))

    override val gestureEvents: SharedFlow<FacialGesture> = eyeMode
        .flatMapLatest { eyeControl -> if (eyeControl) emptyFlow() else camera.gestureEvents }
        .shareIn(scope, SharingStarted.WhileSubscribed(0), replay = 0)

    override val dwellEvents: SharedFlow<GazeDwell> = eyeMode
        .flatMapLatest { eyeControl -> if (eyeControl) eyes.dwellEvents else emptyFlow() }
        .shareIn(scope, SharingStarted.WhileSubscribed(0), replay = 0)

    /** Always null in eye mode: the SDK draws its own preview into a `TextureView` instead. */
    override val surfaceRequest: StateFlow<SurfaceRequest?> = eyeMode
        .flatMapLatest { eyeControl -> if (eyeControl) eyes.surfaceRequest else camera.surfaceRequest }
        .stateIn(scope, SharingStarted.WhileSubscribed(0), null)

    /** The same permission for both engines, so either can answer. */
    override val hasCameraPermission: Boolean get() = camera.hasCameraPermission

    override fun refreshPermissions() {
        camera.refreshPermissions()
        eyes.refreshPermissions()
    }

    /** Head tracking recentres its relative pointer; the gaze pointer is absolute and cannot move. */
    override fun recenterCursor() {
        camera.recenterCursor()
        eyes.recenterCursor()
    }

    override suspend fun captureJoystickCenter(): Boolean =
        if (eyeMode.value) false else camera.captureJoystickCenter()

    private companion object {
        /** The name this router registers under, so acquiring twice cannot leak a count. */
        const val EYE_HOLDER = "input-mode"
    }
}
