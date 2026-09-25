package com.pwde.app.data.gabai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.pwde.app.data.local.GabAiSessionDao
import com.pwde.app.data.local.GabAiSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** One GabAI conversation: where it is, and what's been entered so far. */
data class GabAiSession(val id: String, val state: GabAiState, val form: GabAiForm)

/**
 * Persists GabAI sessions (state + form, keyed by session id) so leaving or force-closing the app
 * resumes exactly where the user was. Also owns game screenshots copied into app storage.
 */
class GabAiRepository(
    private val context: Context,
    private val dao: GabAiSessionDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val unfinished: Flow<GabAiSession?> = dao.observeUnfinished().map { it?.toSession() }

    suspend fun unfinishedSession(): GabAiSession? = dao.getUnfinished()?.toSession()

    suspend fun session(id: String): GabAiSession? = dao.get(id)?.toSession()

    fun newSessionId(): String = UUID.randomUUID().toString()

    /** Saves progress. A session that was already completed stays completed. */
    suspend fun save(session: GabAiSession) {
        val existing = dao.get(session.id)
        if (existing?.completed == true) return
        // A brand-new session: earlier finished ones have nothing left to write, so tidy them away.
        if (existing == null) dao.deleteCompleted()
        dao.upsert(
            GabAiSessionEntity(
                sessionId = session.id,
                stateJson = GabAiCodec.encodeState(session.state),
                formJson = GabAiCodec.encodeForm(session.form),
                completed = false,
                updatedAt = clock(),
            ),
        )
    }

    /** The session is finished (profile saved or abandoned); it no longer offers "continue". */
    suspend fun complete(sessionId: String) {
        dao.markCompleted(sessionId, clock())
    }

    /** Copies a picked image into app storage, returning its path, or null if it can't be read. */
    suspend fun importScreenshot(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, SCREENSHOT_DIR).apply { mkdirs() }
            val file = File(dir, "${UUID.randomUUID()}.img")
            context.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
                ?: return@runCatching null
            // Only keep it if it's actually a readable image.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            if (bounds.outWidth <= 0) {
                file.delete()
                null
            } else {
                file.path
            }
        }.getOrNull()
    }

    /** Loads a stored screenshot, scaled down to at most [maxSide] pixels. */
    suspend fun loadScreenshot(path: String?, maxSide: Int = 1600): Bitmap? = withContext(Dispatchers.IO) {
        if (path == null || !File(path).exists()) return@withContext null
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
            BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }

    private fun GabAiSessionEntity.toSession() =
        GabAiSession(sessionId, GabAiCodec.decodeState(stateJson), GabAiCodec.decodeForm(formJson))

    private companion object {
        const val SCREENSHOT_DIR = "game_screenshots"
    }
}
