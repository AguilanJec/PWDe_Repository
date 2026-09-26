package com.pwde.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.pwde.app.BuildConfig

@Database(
    entities = [CalibrationProfile::class, GameProfile::class, ControlSettingsEntity::class, GabAiSessionEntity::class],
    version = 5,
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

        fun create(context: Context): PwdeDatabase {
            val builder = Room.databaseBuilder(context, PwdeDatabase::class.java, "pwde.db")
                .addMigrations(MIGRATION_3_4, MIGRATION_4_5)
            // Installing a build older than the one already on the device asks Room to walk the
            // schema *down*, and there is no migration for that here: this file only ever moves
            // forward. Without a fallback the app dies on the splash screen with
            // "A migration from N to N-1 was required but not found", which points at Room rather
            // than at the actual cause (a rolled-back install) and is miserable to diagnose.
            //
            // Debug only, on purpose. On a dev device a wiped database costs a re-run of setup; in
            // release it would silently delete the user's calibration and game profiles, so a
            // release build keeps failing loudly instead.
            if (BuildConfig.DEBUG) {
                builder.fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            }
            return builder.build()
        }
    }
}
