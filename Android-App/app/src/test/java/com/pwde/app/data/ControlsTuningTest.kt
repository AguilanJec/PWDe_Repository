package com.pwde.app.data

import android.content.Context
import androidx.room.Room
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.pwde.app.data.local.ControlsRepository
import com.pwde.app.data.local.PwdeDatabase
import com.pwde.app.data.model.CursorTuning
import com.pwde.app.data.model.FacialGesture
import com.pwde.app.data.model.JoystickTuning
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.json.JSONObject
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class ControlsTuningTest {
    private lateinit var db: PwdeDatabase
    private lateinit var controls: ControlsRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, PwdeDatabase::class.java).allowMainThreadQueries().build()
        controls = ControlsRepository(db.controlSettingsDao()) { 1_000L }
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun defaultsWhenNothingSaved() = runTest {
        val config = controls.config.first()
        assertEquals(CursorTuning(), config.cursor)
        assertEquals(JoystickTuning(), config.joystick)
        assertEquals(5, config.sensitivityOf(FacialGesture.SMILE))
    }

    @Test
    fun cursorTuningPersistsAndClamps() = runTest {
        controls.setCursorTuning(CursorTuning(speedUp = 9, speedDown = 2, speedLeft = 14, speedRight = 0, smoothing = 3))
        assertEquals(CursorTuning(9, 2, 10, 1, 3), controls.config.first().cursor)
    }

    @Test
    fun joystickTuningKeepsTheSavedCenter() = runTest {
        controls.setJoystickCenter(pitch = -8f, roll = 4f)
        controls.setJoystickTuning(JoystickTuning(size = 8, sensitivity = 3, deadZone = 6))
        val joystick = controls.config.first().joystick
        assertEquals(JoystickTuning(8, 3, 6, centerPitch = -8f, centerRoll = 4f), joystick)
    }

    @Test
    fun gestureSensitivityIsPerGesture() = runTest {
        controls.setGestureSensitivity(FacialGesture.WINK, 9)
        controls.setGestureSensitivity(FacialGesture.NOD, 42)
        val config = controls.config.first()
        assertEquals(9, config.sensitivityOf(FacialGesture.WINK))
        assertEquals(10, config.sensitivityOf(FacialGesture.NOD))
        assertEquals(5, config.sensitivityOf(FacialGesture.SMILE))
    }

    @Test
    fun tuningDoesNotDisturbVoiceSettings() = runTest {
        controls.setVoiceEnabled(false)
        controls.setCursorTuning(CursorTuning(speedUp = 7))
        assertEquals(false, controls.config.first().voiceEnabled)
    }
}

/**
 * v1 (Prompt 1) → v2 (Prompt 2) keeps existing rows and fills the new columns with defaults.
 * Builds a genuine v1 database from the exported 1.json, then lets Room open it and run the real
 * AutoMigration. (MigrationTestHelper's driver can't open files under Robolectric on Windows.)
 */
@RunWith(RobolectricTestRunner::class)
class MigrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun createVersion1(name: String) {
        val schema = JSONObject(File("schemas/com.pwde.app.data.local.PwdeDatabase/1.json").readText()).getJSONObject("database")
        val file = context.getDatabasePath(name).apply { parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.optJSONArray("indices") ?: continue
                for (j in 0 until indices.length()) {
                    db.execSQL(indices.getJSONObject(j).getString("createSql").replace("\${TABLE_NAME}", table))
                }
            }
            val setup = schema.getJSONArray("setupQueries")
            for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
            db.execSQL(
                "INSERT INTO control_settings (id, gestureAssignmentsJson, voiceEnabled, voiceMatchMode, voiceActivationMode, voiceShortcutsJson, updatedAt) " +
                    "VALUES (0, '{\"SELECT\":\"SMILE\"}', 0, 'EXACT', 'AFTER_FINISH', '{}', 5)",
            )
            db.execSQL(
                "INSERT INTO calibration_profiles (id, name, inputMode, cursorSpeedUp, cursorSpeedDown, cursorSpeedLeft, cursorSpeedRight, " +
                    "joystickSensitivity, joystickDeadZone, joystickRadius, gestureAssignmentsJson, voiceEnabled, voiceMatchMode, " +
                    "voiceActivationMode, createdAt, updatedAt) VALUES (1, 'Mine', 'HEAD_FACE', 4, 5, 6, 7, 5, 3, 5, '{}', 1, 'EXACT', 'IMMEDIATE', 1, 1)",
            )
            db.version = 1
        }
    }

    @Test
    fun migrate1To2() = runTest {
        createVersion1(DB)
        val db = Room.databaseBuilder(context, PwdeDatabase::class.java, DB).allowMainThreadQueries().build()
        try {
            val controls = ControlsRepository(db.controlSettingsDao()).config.first()
            assertEquals(false, controls.voiceEnabled)
            assertEquals(FacialGesture.SMILE, controls.gestureAssignments.values.single())
            assertEquals(CursorTuning(), controls.cursor)
            assertEquals(JoystickTuning(), controls.joystick)
            assertEquals(emptyMap<FacialGesture, Int>(), controls.gestureSensitivity)

            val profile = db.calibrationProfileDao().getById(1)!!
            assertEquals("Mine", profile.name)
            assertEquals(4, profile.cursorSpeedUp)
            assertEquals(7, profile.cursorSmoothing)
            assertEquals("{}", profile.gestureSensitivityJson)
        } finally {
            db.close()
            context.deleteDatabase(DB)
        }
    }

    private companion object {
        const val DB = "migration-test.db"
    }
}
