package com.pwde.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CalibrationProfile::class, GameProfile::class, ControlSettingsEntity::class, GabAiSessionEntity::class],
    version = 6,
    exportSchema = true,
    autoMigrations = [
        // v2 (Prompt 2): cursor/joystick tuning and per-gesture sensitivity.
        AutoMigration(from = 1, to = 2),
        // v3 (Prompt 3): GabAI sessions, joystick center on calibration profiles.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class PwdeDatabase : RoomDatabase() {
    abstract fun calibrationProfileDao(): CalibrationProfileDao
    abstract fun gameProfileDao(): GameProfileDao
    abstract fun controlSettingsDao(): ControlSettingsDao
    abstract fun gabAiSessionDao(): GabAiSessionDao

    companion object {
        /**
         * v4: lastPlayedAt on game profiles, for "play <game>" by voice. Written by hand because an
         * AutoMigration needs 4.json, which only a successful build can export.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `game_profiles` ADD COLUMN `lastPlayedAt` INTEGER")
            }
        }

        /**
         * v5: the gestures a calibration's gesture test enabled. Written by hand because v4's schema
         * was never exported, which an AutoMigration needs. Existing rows stay null: never tested, all on.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `calibration_profiles` ADD COLUMN `enabledGesturesJson` TEXT")
                db.execSQL("ALTER TABLE `control_settings` ADD COLUMN `enabledGesturesJson` TEXT")
            }
        }

        /** v6: remembers which calibration profile the working Controls are editing. */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `control_settings` ADD COLUMN `activeCalibrationProfileId` INTEGER DEFAULT NULL")
            }
        }

        fun create(context: Context): PwdeDatabase =
            Room.databaseBuilder(context, PwdeDatabase::class.java, "pwde.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
                .addMigrations(MIGRATION_5_6)
                .build()
    }
}
