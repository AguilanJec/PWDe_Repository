package com.pwde.app.ui

import android.content.Context
import androidx.camera.core.SurfaceRequest
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pwde.app.data.gabai.Axis
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.PwdeDatabase
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.prefs.AccessibilityNeed
import com.pwde.app.data.prefs.ColorSchemeOption
import com.pwde.app.data.prefs.LayoutMode
import com.pwde.app.data.prefs.TextSizeOption
import com.pwde.app.sensors.face.FaceState
import com.pwde.app.sensors.face.FaceTrackingManager
import com.pwde.app.ui.setup.SetupStep
import com.pwde.app.ui.setup.SetupViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

private class FakeFaceTracking : FaceTrackingManager {
    override val state = MutableStateFlow(FaceState())
    override val gestureEvents = MutableSharedFlow<FacialGesture>()
    override val surfaceRequest = MutableStateFlow<SurfaceRequest?>(null)
    override val hasCameraPermission = false
    override fun refreshPermissions() = Unit
    override fun recenterCursor() = Unit
    override suspend fun captureJoystickCenter() = false
}

@RunWith(RobolectricTestRunner::class)
class SetupViewModelTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    private val settings = FakeSettingsRepository()
    private lateinit var db: PwdeDatabase
    private lateinit var controls: ControlsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // Direct executors keep Room on the test thread, so the ViewModel's launches finish before asserts.
        db = Room.inMemoryDatabaseBuilder(context, PwdeDatabase::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor { it.run() }
            .setTransactionExecutor { it.run() }
            .build()
        controls = ControlsRepository(db.controlSettingsDao())
    }

    @After
    fun tearDown() = db.close()

    private fun viewModel(appearanceOnly: Boolean = false) =
        SetupViewModel(settings, controls, appearanceOnly, FakeFaceTracking())

    private fun SetupViewModel.goTo(step: SetupStep) {
        while (state.value.step != step) skipStep()
    }

    @Test
    fun steps_runInOrder() {
        assertEquals(
            listOf(
                SetupStep.TURN_ON,
                SetupStep.NEEDS,
                SetupStep.PERMISSIONS,
                SetupStep.APPEARANCE,
                SetupStep.CURSOR_CALIBRATION,
            ),
            viewModel().state.value.steps,
        )
    }

    @Test
    fun continue_persistsEachStep_andFinishMarksSetupComplete() {
        val vm = viewModel()

        vm.continueStep()
        assertEquals(SetupStep.NEEDS, vm.state.value.step)

        vm.toggleNeed(AccessibilityNeed.MOVEMENT)
        vm.continueStep()
        assertEquals(setOf(AccessibilityNeed.MOVEMENT), settings.settings.value.accessibilityNeeds)
        assertEquals(SetupStep.PERMISSIONS, vm.state.value.step)

        vm.continueStep()
        assertEquals(SetupStep.APPEARANCE, vm.state.value.step)

        vm.setColorScheme(ColorSchemeOption.LIGHT)
        vm.setTextSize(TextSizeOption.LARGE)
        vm.setLayoutMode(LayoutMode.EASY_REACH)
        vm.continueStep()
        with(settings.settings.value) {
            assertEquals(ColorSchemeOption.LIGHT, colorScheme)
            assertEquals(TextSizeOption.LARGE, textSize)
            assertEquals(LayoutMode.EASY_REACH, layoutMode)
        }
        assertEquals(SetupStep.CURSOR_CALIBRATION, vm.state.value.step)
        assertFalse(settings.settings.value.setupCompleted)

        vm.continueStep()
        assertTrue(settings.settings.value.setupCompleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun skip_doesNotPersist_andResetsTheDraftPreview() {
        val vm = viewModel()
        vm.goTo(SetupStep.APPEARANCE)

        vm.setColorScheme(ColorSchemeOption.CONTRAST)
        vm.skipStep()

        assertEquals(ColorSchemeOption.DEFAULT, settings.settings.value.colorScheme)
        assertEquals(ColorSchemeOption.DEFAULT, vm.state.value.colorScheme)
        assertEquals(SetupStep.CURSOR_CALIBRATION, vm.state.value.step)
    }

    @Test
    fun appearanceOnly_hasOneStep_andDoesNotTouchSetupCompleted() {
        val vm = viewModel(appearanceOnly = true)
        assertEquals(listOf(SetupStep.APPEARANCE), vm.state.value.steps)

        vm.setTextSize(TextSizeOption.X_LARGE)
        vm.continueStep()

        assertEquals(TextSizeOption.X_LARGE, settings.settings.value.textSize)
        assertFalse(settings.settings.value.setupCompleted)
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun back_onFirstStep_returnsFalse() {
        val vm = viewModel()
        assertFalse(vm.back())
        vm.continueStep()
        assertTrue(vm.back())
        assertEquals(SetupStep.TURN_ON, vm.state.value.step)
    }

    @Test
    fun calibration_walksAxes_andBackStepsThroughThemFirst() {
        val vm = viewModel()
        vm.goTo(SetupStep.CURSOR_CALIBRATION)
        assertEquals(Axis.entries.first(), vm.state.value.axis)

        vm.axisDone()
        assertEquals(Axis.entries[1], vm.state.value.axis)
        assertTrue(vm.back())
        assertEquals(Axis.entries.first(), vm.state.value.axis)
        assertEquals(SetupStep.CURSOR_CALIBRATION, vm.state.value.step)

        repeat(Axis.entries.size) { vm.axisDone() }
        assertTrue(vm.state.value.finished)
    }

    @Test
    fun setCursor_savesStraightToControls() {
        val vm = viewModel()
        val tuning = CursorTuning(speedUp = 7, speedDown = 3, speedLeft = 4, speedRight = 6, smoothing = 2)
        vm.setCursor(tuning)
        assertEquals(tuning, vm.state.value.cursor)
        assertEquals(tuning, runBlocking { controls.config.first().cursor })
    }
}
