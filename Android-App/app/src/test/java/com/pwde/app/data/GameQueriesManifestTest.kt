package com.pwde.app.data

import com.pwde.app.data.model.Game
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Android 11+ filters package visibility, so [android.content.pm.PackageManager.getLaunchIntentForPackage]
 * returns null for any package the app has not declared in `<queries>`. An undeclared but *installed*
 * game is therefore reported as "isn't installed — opening the Play Store".
 *
 * The declaration lives in AndroidManifest.xml and the package names live in [Game.packageName], in
 * different files and different languages, so nothing else can catch the drift before a user does.
 */
class GameQueriesManifestTest {

    @Test
    fun everySupportedGame_isDeclaredInQueries() {
        val manifest = readManifest()
        val queries = manifest.substringAfter("<queries>", "").substringBefore("</queries>")
        val declared = Regex("""<package\s+android:name="([^"]+)"""")
            .findAll(queries)
            .map { it.groupValues[1] }
            .toSet()

        val missing = Game.entries.map { it.packageName }.filterNot { it in declared }
        assertTrue(
            "These packages are missing from <queries> in app/src/main/AndroidManifest.xml, so " +
                "getLaunchIntentForPackage() returns null for them on Android 11+ and an installed " +
                "game looks missing: $missing",
            missing.isEmpty(),
        )
    }

    /**
     * Guards the parsing above: if `<queries>` is renamed or dropped, [queries] becomes empty and the
     * other test would still be the one to fail — but only because every game looked missing. Fail
     * here instead, so the cause is unambiguous.
     */
    @Test
    fun queriesElement_exists() {
        assertTrue(
            "No <queries> element in app/src/main/AndroidManifest.xml",
            readManifest().contains("<queries>"),
        )
    }

    /** Gradle's test working directory differs between IDE and CLI runs, so search upwards. */
    private fun readManifest(): String {
        val relative = "src/main/AndroidManifest.xml"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            for (candidate in listOf(File(dir, relative), File(dir, "app/$relative"))) {
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        error("Could not find $relative from ${File("").absolutePath}")
    }
}
