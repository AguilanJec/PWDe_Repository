package com.pwde.app.ui.common

import androidx.camera.core.SurfaceRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

/**
 * Base for screens that show live head/face tracking. Observing [faceState] is what turns the
 * camera on; it turns off a few seconds after the screen stops observing.
 */
open class FaceTrackingViewModel(protected val faceTracking: FaceTrackingManager) : ViewModel() {
    val faceState: StateFlow<FaceState> = faceTracking.state
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), faceTracking.state.value)

    val surfaceRequest: StateFlow<SurfaceRequest?> = faceTracking.surfaceRequest

    /** True while the camera permission hasn't been granted, so the UI can offer to ask. */
    val canRequestCamera: Boolean get() = !faceTracking.hasCameraPermission

    fun onCameraPermissionResult() = faceTracking.refreshPermissions()

    fun recenterCursor() = faceTracking.recenterCursor()
}
