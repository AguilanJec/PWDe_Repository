package com.pwde.app.sensors

import com.pwde.app.data.model.VoiceActivationMode
import com.pwde.app.data.model.VoiceMatchMode
import com.pwde.app.sensors.voice.BaseInGameVoiceEngine
import com.pwde.app.sensors.voice.InGameVoiceResult
import com.pwde.app.sensors.voice.MicArbiter
import com.pwde.app.sensors.voice.VoiceCommandBinding
import com.pwde.app.play.GameInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A bare engine to exercise the contract every implementation inherits from the base class. */
private class TestEngine : BaseInGameVoiceEngine() {
    var started = false
    override fun start() {
        started = true
    }
    override fun stop() {
        started = false
    }
    fun hear(vararg text: String, isFinal: Boolean = true, confidences: FloatArray? = null) =
        onTranscript(text.toList(), confidences, isFinal)
    fun configure(match: VoiceMatchMode, activation: VoiceActivationMode) {
        matchMode = match
        activationMode = activation
    }
}

class InGameVoiceEngineTest {
    private val gameCommands = GameInput.STANDARD_BINDINGS + VoiceCommandBinding("button:1", listOf("attack"))

    private fun collect(engine: TestEngine, block: () -> Unit): List<InGameVoiceResult> = mutableListOf<InGameVoiceResult>().also { out ->
        runTest(UnconfinedTestDispatcher()) {
            val job = engine.results.onEach { out += it }.launchIn(this)
            block()
            job.cancel()
        }
    }

    @Test
    fun onlyLoadedCommandsAreRecognized() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        val results = collect(engine) {
            engine.hear("attack now")
            engine.hear("settings") // an app-wide command, not a game one
        }
        assertEquals("button:1", results[0].commandId)
        assertEquals(null, results[1].commandId)
        assertEquals("settings", results[1].rawText)
    }

    @Test
    fun backPauseAndMenuAreAlwaysThere() {
        val engine = TestEngine().apply { loadCommands(GameInput.STANDARD_BINDINGS) }
        val results = collect(engine) {
            engine.hear("go back")
            engine.hear("pause")
            engine.hear("menu")
        }
        assertEquals(listOf(GameInput.BACK, GameInput.PAUSE, GameInput.MENU), results.map { it.commandId })
    }

    @Test
    fun reloadingReplacesTheCommandSet() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        engine.loadCommands(GameInput.STANDARD_BINDINGS)
        val results = collect(engine) { engine.hear("attack") }
        assertEquals(null, results.single().commandId)
    }

    @Test
    fun typedFallbackMatchesLikeSpeech() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        val results = collect(engine) {
            engine.submitText("Attack!")
            engine.submitText("dance")
        }
        assertEquals("button:1", results[0].commandId)
        assertEquals(InGameVoiceResult.CONFIDENCE_TYPED, results[0].confidence)
        assertEquals(null, results[1].commandId)
    }

    @Test
    fun immediateFiresOnPartialOnceAfterFinishWaits() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        val immediate = collect(engine) {
            engine.hear("attack", isFinal = false)
            engine.hear("attack", isFinal = true)
        }
        assertEquals(listOf("button:1"), immediate.map { it.commandId })

        engine.configure(VoiceMatchMode.WORD_ANYWHERE, VoiceActivationMode.AFTER_FINISH)
        val afterFinish = collect(engine) {
            engine.hear("attack", isFinal = false)
            engine.hear("attack", isFinal = true)
        }
        assertEquals(listOf("button:1"), afterFinish.map { it.commandId })
    }

    @Test
    fun partialNonMatchesAreSilent() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        assertTrue(collect(engine) { engine.hear("hmm", isFinal = false) }.isEmpty())
    }

    @Test
    fun confidenceComesFromTheMatchingHypothesis() {
        val engine = TestEngine().apply { loadCommands(gameCommands) }
        val results = collect(engine) { engine.hear("a tack", "attack", confidences = floatArrayOf(0.4f, 0.9f)) }
        assertEquals(0.9f, results.single().confidence, 0f)
        val unknown = collect(engine) { engine.hear("attack") }
        assertEquals(InGameVoiceResult.CONFIDENCE_UNKNOWN, unknown.single().confidence)
    }

    @Test
    fun micArbiterHandsTheMicToTheGameAndBack() {
        val arbiter = MicArbiter()
        assertFalse(arbiter.gameHasMic.value)
        arbiter.takeForGame()
        assertTrue(arbiter.gameHasMic.value)
        arbiter.releaseFromGame()
        assertFalse(arbiter.gameHasMic.value)
    }

    @Test
    fun micArbiterIsBusyWhileTheGameOrTheWakeWordHasTheMic() = runTest {
        val arbiter = MicArbiter()
        assertFalse(arbiter.busy.first())
        arbiter.takeForWakeWord()
        assertTrue(arbiter.busy.first())
        arbiter.takeForGame()
        arbiter.releaseFromWakeWord()
        assertTrue(arbiter.busy.first())
        arbiter.releaseFromGame()
        assertFalse(arbiter.busy.first())
    }
}
