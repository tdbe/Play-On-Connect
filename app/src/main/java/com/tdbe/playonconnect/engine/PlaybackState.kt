package com.tdbe.playonconnect.engine

import kotlinx.coroutines.flow.MutableStateFlow

object PlaybackState {
    data class Playing(val ruleId: String, val label: String)

    val current = MutableStateFlow<Playing?>(null)
}
