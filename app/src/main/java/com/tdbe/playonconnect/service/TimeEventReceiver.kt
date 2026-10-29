package com.tdbe.playonconnect.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class TimeEventReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_TIME_EVENT = "com.tdbe.playonconnect.action.TIME_EVENT"
    }

    override fun onReceive(context: Context, intent: Intent?) {
        val ruleId = intent?.getStringExtra(TimeScheduler.EXTRA_RULE_ID) ?: return
        ContextCompat.startForegroundService(
            context,
            PlaybackService.timeEventIntent(context, ruleId),
        )
    }
}
