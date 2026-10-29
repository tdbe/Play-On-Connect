package com.tdbe.playonconnect.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Rule(
    val id: String = UUID.randomUUID().toString(),
    val enabled: Boolean = true,
    // True while a delete is waiting for the undo window to expire.
    val pendingDelete: Boolean = false,
    val trigger: Trigger,
    val action: Action,
)

@Serializable
sealed interface Trigger {
    @Serializable
    @SerialName("bluetoothDeviceConnected")
    data class BluetoothDeviceConnected(
        val deviceAddress: String,
        val deviceName: String? = null,
    ) : Trigger

    @Serializable
    @SerialName("timeEvent")
    data class TimeEvent(
        // Format: "hh:mm:ss repeat:Mo,Tu,We,Th,Fr,Sa,Su" (repeat part optional).
        val spec: String,
    ) : Trigger
}

@Serializable
sealed interface Action {
    @Serializable
    @SerialName("playSound")
    data class PlaySound(
        val uri: String,
        // Playlist support. `uri` is kept as the primary/legacy field so old
        // saved rules keep working; when uris has entries it takes precedence.
        val uris: List<String>? = null,
        val displayName: String? = null,
        val displayNames: List<String>? = null,
        val loop: Boolean = false,
        val maxMinutes: Double? = null,
        val fadeSeconds: Int? = null,
    ) : Action {
        val effectiveUris: List<String>
            get() = uris?.takeIf { it.isNotEmpty() } ?: listOf(uri)

        val effectiveNames: List<String>
            get() = displayNames?.takeIf { it.isNotEmpty() } ?: listOfNotNull(displayName)
    }

    @Serializable
    @SerialName("openUrl")
    data class OpenUrl(
        val url: String = "",
        val useDedicatedApp: Boolean = false,
        val browserPackage: String? = null,
        val appPackage: String? = null,
        val maxMinutes: Double? = null,
    ) : Action
}
