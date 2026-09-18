package com.maipu.callsync.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri

class AudioPlaybackController(
    context: Context,
    private val onStateChanged: (PlaybackState) -> Unit,
) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null
    private var activeUri: Uri? = null

    fun toggle(uri: Uri) {
        val currentPlayer = player
        if (activeUri == uri && currentPlayer != null) {
            if (currentPlayer.isPlaying) {
                currentPlayer.pause()
                onStateChanged(PlaybackState.Paused(uri))
            } else {
                currentPlayer.start()
                onStateChanged(PlaybackState.Playing(uri))
            }
            return
        }

        release()
        activeUri = uri
        onStateChanged(PlaybackState.Loading(uri))

        try {
            player = MediaPlayer().apply {
                setDataSource(appContext, uri)
                setOnPreparedListener {
                    it.start()
                    onStateChanged(PlaybackState.Playing(uri))
                }
                setOnCompletionListener {
                    onStateChanged(PlaybackState.Completed(uri))
                    release()
                }
                setOnErrorListener { _, what, extra ->
                    onStateChanged(
                        PlaybackState.Error(
                            uri,
                            "MediaPlayer no pudo reproducir el URI (what=$what, extra=$extra).",
                        ),
                    )
                    release()
                    true
                }
                prepareAsync()
            }
        } catch (exception: Exception) {
            onStateChanged(
                PlaybackState.Error(
                    uri,
                    "No se pudo abrir el Content URI: " +
                        (exception.message ?: exception.javaClass.simpleName),
                ),
            )
            release()
        }
    }

    fun release() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        activeUri = null
    }
}

sealed interface PlaybackState {
    data object Idle : PlaybackState
    data class Loading(val uri: Uri) : PlaybackState
    data class Playing(val uri: Uri) : PlaybackState
    data class Paused(val uri: Uri) : PlaybackState
    data class Completed(val uri: Uri) : PlaybackState
    data class Error(val uri: Uri, val message: String) : PlaybackState
}