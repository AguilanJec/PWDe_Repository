package com.pwde.app.sensors.voice

import android.content.Context
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.model.ControlConfig
import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.data.prefs.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class MicAvailability(val label: String) {
    AVAILABLE("Microphone ready"),
    NO_PERMISSION("Microphone permission is off"),
    NO_RECOGNIZER("No speech recognition service on this phone"),
    SERVICE_ERROR("Speech recognition keeps failing"),
}

enum class VoiceInputSource { MIC, TEXT }

/** One transcript (partial or final). [command] is set only when it fired. */
data class VoiceResult(
    val transcript: String,
    val isFinal: Boolean,
    val command: VoiceCommand?,
    val source: VoiceInputSource,
)

data class VoiceState(
    /** The user's on/off setting. */
    val enabled: Boolean = false,
    val availability: MicAvailability = MicAvailability.AVAILABLE,
    val listening: Boolean = false,
    /** Mic input level, 0–1, for the waveform. */
    val level: Float = 0f,
    val lastTranscript: String? = null,
    val lastCommand: VoiceCommand? = null,
    val matchMode: VoiceMatchMode = VoiceMatchMode.WORD_ANYWHERE,
    val activationMode: VoiceActivationMode = VoiceActivationMode.IMMEDIATE,
    /** Everything that can be said right now: screen commands first, then global ones. */
    val commands: List<VoiceCommand> = emptyList(),
    /** Gameplay's in-game voice engine has the microphone; app-wide voice is standing down. */
    val pausedForGame: Boolean = false,
) {
    /** Typed commands take over when the mic can't be used. */
    val usesTextFallback: Boolean get() = availability != MicAvailability.AVAILABLE
}

/**
 * App-scoped voice commands (no system-wide listening). Listens only while voice is on and
 * someone observes [state] — i.e. while PWDe is on screen — and never while a game holds the mic.
 */
interface VoiceCommandManager {
    val state: StateFlow<VoiceState>
    val results: SharedFlow<VoiceResult>
    val hasMicPermission: Boolean

    fun refreshPermissions()

    /** Typed fallback: same matching, same command catalog. */
    fun submitText(text: String)

    /** Commands for whatever [owner] (a screen) is showing. Replaces that owner's previous list. */
    fun setScreenCommands(owner: Any, commands: List<VoiceCommand>)

    fun clearScreenCommands(owner: Any)

    /**
     * While any owner is dictating, utterances starting with "assign" or "use" ([Dictation]) never
     * fire commands; the owner reads them from [results] instead.
     */
    fun setDictating(owner: Any, dictating: Boolean)
}

class AndroidVoiceCommandManager(
    context: Context,
    private val controlsRepository: ControlsRepository,
    private val settingsRepository: SettingsRepository,
    private val micArbiter: MicArbiter,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : VoiceCommandManager {
    private val appContext = context.applicationContext
    private val _state = MutableStateFlow(VoiceState())
    private val _results = MutableSharedFlow<VoiceResult>(extraBufferCapacity = 16)
    private val permissionTick = MutableStateFlow(0)
    private val screenCommands = MutableStateFlow<Map<Any, List<VoiceCommand>>>(emptyMap())
    private val dictationOwners = MutableStateFlow<Set<Any>>(emptySet())
    private val gate = VoiceActivationGate()
    private var config = ControlConfig()

    private val recognizer = ContinuousSpeechRecognizer(appContext, scope, object : ContinuousSpeechRecognizer.Listener {
        override fun onListening(listening: Boolean) = _state.update { it.copy(listening = listening) }
        override fun onLevel(level: Float) = _state.update { it.copy(level = level) }
        override fun onUtteranceAborted() = gate.reset()
        override fun onUnavailable(reason: MicAvailability) = _state.update { it.copy(availability = reason) }

        override fun onHeard(hypotheses: List<String>, confidences: FloatArray?, isFinal: Boolean) {
            val match = if (isDictation(hypotheses.first())) null else CommandMatcher.match(hypotheses, allCommands(), config.voiceMatchMode)
            val fired = gate.offer(match, isFinal, config.voiceActivationMode)
            publish(hypotheses.first(), isFinal, fired, VoiceInputSource.MIC)
        }
    })

    override val state: StateFlow<VoiceState> = _state.asStateFlow()
    override val results: SharedFlow<VoiceResult> = _results.asSharedFlow()

    override val hasMicPermission: Boolean get() = ContinuousSpeechRecognizer.hasMicPermission(appContext)

    init {
        scope.launch {
            _state.subscriptionCount.map { it > 0 }.distinctUntilChanged().collectLatest { observed ->
                if (!observed) {
                    stopListening()
                    return@collectLatest
                }
                try {
                    combine(
                        controlsRepository.config,
                        permissionTick,
                        screenCommands,
                        micArbiter.gameHasMic,
                        settingsRepository.settings.map { it.pwdeEnabled }.distinctUntilChanged(),
                    ) { config, _, screens, gameHasMic, pwdeEnabled -> ListenInputs(config, screens, gameHasMic, pwdeEnabled) }
                        .collect { (latest, screens, gameHasMic, pwdeEnabled) ->                            config = latest
                            val availability = recognizer.availability()
                            _state.update {
                                it.copy(
                                    enabled = latest.voiceEnabled,
                                    availability = availability,
                                    matchMode = latest.voiceMatchMode,
                                    activationMode = latest.voiceActivationMode,
                                    commands = screens.values.flatten() + globalCommands(latest),
                                    pausedForGame = gameHasMic,
                                )
                            }
                            // The home screen's master switch wins: PWDe off means nothing listens.
                            val listen = pwdeEnabled && latest.voiceEnabled && availability == MicAvailability.AVAILABLE && !gameHasMic
                            if (listen) recognizer.start() else stopListening()
                        }
                } finally {
                    stopListening()
                }
            }
        }
    }

    override fun refreshPermissions() {
        recognizer.resetErrors()
        permissionTick.value++
    }

    override fun submitText(text: String) {
        if (text.isBlank()) return
        scope.launch {
            val command = if (isDictation(text)) null else CommandMatcher.match(text, allCommands(), config.voiceMatchMode)
            publish(text.trim(), isFinal = true, command = command, source = VoiceInputSource.TEXT)
        }
    }

    override fun setScreenCommands(owner: Any, commands: List<VoiceCommand>) {
        screenCommands.update { it + (owner to commands) }
    }

    override fun clearScreenCommands(owner: Any) {
        screenCommands.update { it - owner }
    }

    override fun setDictating(owner: Any, dictating: Boolean) {
        dictationOwners.update { if (dictating) it + owner else it - owner }
    }

    private fun isDictation(text: String) = dictationOwners.value.isNotEmpty() && Dictation.isAssignment(text)

    private fun globalCommands(config: ControlConfig) =
        StandardCommands.all + StandardCommands.playGames + StandardCommands.shortcuts(config.voiceShortcuts)

    private fun allCommands() = screenCommands.value.values.flatten() + globalCommands(config)

    private fun stopListening() {
        recognizer.stop()
        gate.reset()
    }

    private fun publish(transcript: String, isFinal: Boolean, command: VoiceCommand?, source: VoiceInputSource) {
        _state.update { it.copy(lastTranscript = transcript, lastCommand = command ?: it.lastCommand) }
        _results.tryEmit(VoiceResult(transcript, isFinal, command, source))
    }

    private data class ListenInputs(
        val config: ControlConfig,
        val screens: Map<Any, List<VoiceCommand>>,
        val gameHasMic: Boolean,
        val pwdeEnabled: Boolean,
    )
}
