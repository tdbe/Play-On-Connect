package com.tdbe.playonconnect.engine

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat

class BluetoothMonitor(private val context: Context) {

    private companion object {
        const val TAG = "BluetoothMonitor"
        const val A2DP_CONNECTION_STATE_CHANGED =
            "android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED"
        const val HEADSET_CONNECTION_STATE_CHANGED =
            "android.bluetooth.headset.profile.action.CONNECTION_STATE_CHANGED"
    }

    private var receiver: BroadcastReceiver? = null

    fun start(onEvent: (address: String, connected: Boolean) -> Unit) {
        if (receiver != null) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val address = device?.address ?: return
                when (intent.action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> onEvent(address, true)
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> onEvent(address, false)
                    A2DP_CONNECTION_STATE_CHANGED, HEADSET_CONNECTION_STATE_CHANGED -> {
                        when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                            BluetoothProfile.STATE_CONNECTED -> onEvent(address, true)
                            BluetoothProfile.STATE_DISCONNECTED -> onEvent(address, false)
                        }
                    }

                    else -> return
                }
                Log.d(TAG, "Broadcast received: ${intent.action} from $address")
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
            addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            addAction(A2DP_CONNECTION_STATE_CHANGED)
            addAction(HEADSET_CONNECTION_STATE_CHANGED)
        }
        ContextCompat.registerReceiver(context, r, filter, ContextCompat.RECEIVER_EXPORTED)
        receiver = r
    }

    fun stop() {
        receiver?.let { runCatching { context.unregisterReceiver(it) } }
        receiver = null
    }
}
