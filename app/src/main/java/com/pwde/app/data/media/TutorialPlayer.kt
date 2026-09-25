package com.pwde.app.data.media

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.pwde.app.R

/** Owns the ExoPlayer for the tutorial video. Create one per screen and [release] it. */
class TutorialPlayer(context: Context) {
    val player: Player = ExoPlayer.Builder(context.applicationContext).build().apply {
        val uri = "android.resource://${context.packageName}/${R.raw.tutorial_placeholder}"
        setMediaItem(MediaItem.fromUri(uri))
        prepare()
    }

    val isPlaying: Boolean get() = player.isPlaying
    val positionMs: Long get() = player.currentPosition
    val durationMs: Long get() = player.duration.coerceAtLeast(0)
    val hasEnded: Boolean get() = player.playbackState == Player.STATE_ENDED

    fun play() {
        if (hasEnded) player.seekTo(0)
        player.play()
    }

    fun pause() = player.pause()

    fun seekTo(positionMs: Long) = player.seekTo(positionMs.coerceIn(0, durationMs))

    fun release() = player.release()
}
