package com.tdbe.playonconnect.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.tdbe.playonconnect.data.AppSettings
import com.tdbe.playonconnect.data.RuleStore

class AppWakeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        AppSettings.init(context)
        RuleStore.init(context)
        if (RuleStore.rules.value.none { it.enabled }) return
        when (intent?.action) {
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                @Suppress("DEPRECATION")
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                val address = device?.address
                if (address != null) {
                    PlaybackService.notifyConnection(
                        context,
                        address,
                        intent.action == BluetoothDevice.ACTION_ACL_CONNECTED,
                    )
                } else {
                    PlaybackService.start(context)
                }
            }

            else -> PlaybackService.start(context)
        }
    }
}
