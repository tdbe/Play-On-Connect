package com.tdbe.playonconnect.engine

import kotlinx.coroutines.flow.MutableStateFlow

object PlaybackState {
    /** Currently playing rules: ruleId -> display label. */
    val playing = MutableStateFlow<Map<String, String>>(emptyMap())
}
