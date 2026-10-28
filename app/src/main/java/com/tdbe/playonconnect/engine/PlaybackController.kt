package com.tdbe.playonconnect.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.Action

class PlaybackController(private val context: Context) {

    private val handler = Handler(Looper.getMainLooper())
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private var player: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var timeoutRunnable: Runnable? = null
    private val fadeRunnables = mutableListOf<Runnable>()
    private var onFinish: ((error: String?, timedOut: Boolean) -> Unit)? = null
    private var finished = false

    private var playlist: List<Uri> = emptyList()
    private var index = 0
    private var looping = false

    val isPlaying: Boolean get() = player != null

    fun start(action: Action.PlaySound, onFinish: (error: String?, timedOut: Boolean) -> Unit) {
        stop()
        this.onFinish = onFinish
        finished = false
        playlist = action.effectiveUris.map { Uri.parse(it) }
        looping = action.loop
        index = 0

        if (!playCurrentTrack()) {
            finished = true
            val callback = this.onFinish
            this.onFinish = null
            callback?.invoke(errorForCurrentTrack(), false)
            return
        }

        acquireWakeLock(action.maxMinutes)
        action.maxMinutes?.let { minutes ->
            val totalMs = (minutes * 60_000).toLong()
            timeoutRunnable = Runnable { finishInternal(null, true) }.also {
                handler.postDelayed(it, totalMs)
            }
            scheduleFade(action.fadeSeconds, totalMs / 1000L)
        } ?: run {
            // No stop-after: when not looping, derive the end time from the
            // total playlist duration so the fade can still be scheduled.
            if (!action.loop) {
                playlistDurationMs()?.let { scheduleFade(action.fadeSeconds, it / 1000L) }
            }
        }
    }

    fun stop() {
        finishInternal(null, false)
    }

    /**
     * Schedules the fade so nothing runs until the fade actually begins:
     * one delayed event at (endTime - fadeDuration), then 1s steps.
     */
    private fun scheduleFade(fadeSeconds: Int?, totalSeconds: Long) {
        val fade = fadeSeconds?.takeIf { it > 0 } ?: return
        if (totalSeconds <= 0) return
        val steps = minOf(fade.toLong(), totalSeconds).toInt()
        val startDelayMs = (totalSeconds - steps) * 1000L
        val starter = Runnable {
            for (i in 1..steps) {
                val volume = 1f - i.toFloat() / steps.toFloat()
                val step = Runnable { player?.setVolume(volume, volume) }
                fadeRunnables.add(step)
                handler.postDelayed(step, i * 1000L)
            }
        }
        fadeRunnables.add(starter)
        handler.postDelayed(starter, startDelayMs)
    }

    /** Sum of the durations of all playlist files; null if any duration is unknown. */
    private fun playlistDurationMs(): Long? {
        var total = 0L
        for (uri in playlist) {
            val retriever = MediaMetadataRetriever()
            val durationMs = runCatching {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }.getOrNull()
            runCatching { retriever.release() }
            if (durationMs == null || durationMs <= 0L) return null
            total += durationMs
        }
        return total
    }

    private fun playCurrentTrack(): Boolean {
        releasePlayer()
        if (playlist.isEmpty()) return false
        val track = playlist[index]
        val mp = MediaPlayer()
        return try {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            mp.setDataSource(context, track)
            mp.isLooping = looping && playlist.size == 1
            mp.setOnCompletionListener { onTrackCompleted() }
            mp.setOnErrorListener { _, what, extra ->
                finishInternal(context.getString(R.string.error_playback_failed, what, extra), false)
                true
            }
            mp.prepare()
            mp.start()
            player = mp
            true
        } catch (e: Exception) {
            runCatching { mp.release() }
            false
        }
    }

    private fun onTrackCompleted() {
        if (finished) return
        if (index + 1 < playlist.size) {
            index++
            if (!playCurrentTrack()) finishInternal(errorForCurrentTrack(), false)
        } else if (looping) {
            index = 0
            if (!playCurrentTrack()) finishInternal(errorForCurrentTrack(), false)
        } else {
            finishInternal(null, false)
        }
    }

    private fun errorForCurrentTrack(): String {
        val label = playlist.getOrNull(index)?.lastPathSegment ?: "sound"
        return context.getString(R.string.error_couldnt_play, label)
    }

    private fun finishInternal(error: String?, timedOut: Boolean) {
        if (finished) return
        finished = true
        val callback = onFinish
        onFinish = null
        releaseInternal()
        callback?.invoke(error, timedOut)
    }

    private fun releaseInternal() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
        fadeRunnables.forEach { handler.removeCallbacks(it) }
        fadeRunnables.clear()
        releasePlayer()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    private fun releasePlayer() {
        player?.let { mp ->
            runCatching { if (mp.isPlaying) mp.stop() }
            runCatching { mp.release() }
        }
        player = null
    }

    private fun acquireWakeLock(maxMinutes: Double?) {
        val wl = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PlayOnConnect:play")
        wl.setReferenceCounted(false)
        if (maxMinutes != null) {
            wl.acquire((maxMinutes * 60_000).toLong() + 60_000L)
        } else {
            wl.acquire()
        }
        wakeLock = wl
    }
}
