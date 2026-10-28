package com.tdbe.playonconnect.util

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

object BluetoothUtil {

    private const val A2DP_CONNECTION_STATE_CHANGED =
        "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"

    fun hasConnectPermission(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED

    fun getAdapter(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    fun bondedDevices(context: Context): List<BluetoothDevice> {
        val adapter = getAdapter(context) ?: return emptyList()
        if (!hasConnectPermission(context)) return emptyList()
        return runCatching { adapter.bondedDevices?.toList() }.getOrNull().orEmpty()
    }

    fun displayName(device: BluetoothDevice): String =
        runCatching { device.name }.getOrNull()?.takeIf { it.isNotBlank() } ?: device.address

    fun connectedAddresses(context: Context): Flow<Set<String>> = callbackFlow {
        val adapter = getAdapter(context)
        if (adapter == null || !hasConnectPermission(context)) {
            trySend(emptySet())
            close()
            return@callbackFlow
        }
        var proxy: BluetoothProfile? = null

        fun emitConnected(profile: BluetoothProfile) {
            trySend(
                runCatching { profile.connectedDevices?.mapNotNull { it.address }?.toSet() }
                    .getOrNull().orEmpty()
            )
        }

        val listener = object : BluetoothProfile.ServiceListener {
            override fun onServiceConnected(profile: Int, service: BluetoothProfile?) {
                if (profile != BluetoothProfile.A2DP) return
                val p = service ?: return
                proxy = p
                emitConnected(p)
            }

            override fun onServiceDisconnected(profile: Int) {
                proxy = null
                trySend(emptySet())
            }
        }

        val broadcast = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                val p = proxy ?: return
                emitConnected(p)
            }
        }
        ContextCompat.registerReceiver(
            context, broadcast,
            IntentFilter(A2DP_CONNECTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        if (!adapter.getProfileProxy(context, listener, BluetoothProfile.A2DP)) {
            trySend(emptySet())
        }
        awaitClose {
            runCatching { context.unregisterReceiver(broadcast) }
            proxy?.let { p -> runCatching { adapter.closeProfileProxy(BluetoothProfile.A2DP, p) } }
        }
    }

    fun canOpen(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.openInputStream(uri)?.close()
        true
    }.getOrDefault(false)

    fun fileDisplayName(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()
}
