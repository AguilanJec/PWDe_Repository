package com.pwde.app.sensors.voice

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Release twin of the debug factory. sherpa-onnx and its model are debug-only, so release builds
 * get an engine that reports itself unavailable. Nothing in a release build asks for a wake word —
 * the Testing Station's route and screen don't exist there either.
 */
fun createWakeWordEngine(context: Context, micArbiter: MicArbiter): WakeWordEngine = NoopWakeWordEngine()

class NoopWakeWordEngine : WakeWordEngine {
    private val _state = MutableStateFlow(WakeWordState(availability = WakeWordAvailability.UNAVAILABLE))
    override val state: StateFlow<WakeWordState> = _state.asStateFlow()

    private val _detections = MutableSharedFlow<WakeWordDetection>()
    override val detections: SharedFlow<WakeWordDetection> = _detections.asSharedFlow()

    override fun start(
        phrases: List<String>,
        tuning: Map<String, WakeWordTuning>,
        spotter: WakeWordSpotterTuning,
    ) = Unit

    override fun stop() = Unit
}
