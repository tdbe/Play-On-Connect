package com.tdbe.playonconnect.util

import android.content.Context
import android.media.MediaMetadataRetriever

object AudioInfo {

    /** Sum of durations in seconds for all files; null if empty or any duration is unknown. */
    fun totalDurationSec(context: Context, uris: List<String>): Long? {
        if (uris.isEmpty()) return null
        var total = 0L
        for (uriStr in uris) {
            val retriever = MediaMetadataRetriever()
            val durationMs = runCatching {
                retriever.setDataSource(context, android.net.Uri.parse(uriStr))
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            }.getOrNull()
            runCatching { retriever.release() }
            if (durationMs == null || durationMs <= 0L) return null
            total += durationMs
        }
        return total / 1000L
    }
}
