package com.tdbe.playonconnect.service

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.data.Trigger
import com.tdbe.playonconnect.util.TimeSpec

/**
 * Schedules exact alarms for all enabled Time Event rules. Non-repeating rules
 * are suppressed after firing until they get disabled (on playback stop).
 */
object TimeScheduler {

    private const val TAG = "TimeScheduler"
    const val EXTRA_RULE_ID = "extra_time_rule_id"

    private val suppress = mutableSetOf<String>()

    fun rescheduleAll(context: Context) {
        RuleStore.init(context)
        for (rule in RuleStore.rules.value) {
            val trigger = rule.trigger as? Trigger.TimeEvent
            val active = rule.enabled && !rule.pendingDelete && trigger != null
            if (!active) {
                cancel(context, rule.id)
                suppress.remove(rule.id)
                continue
            }
            if (rule.id in suppress) {
                // Already fired once; waits for playback to end (then disabled).
                cancel(context, rule.id)
                continue
            }
            val parsed = TimeSpec.parse(trigger!!.spec) ?: continue
            val next = TimeSpec.nextFireMs(parsed) ?: continue
            schedule(context, rule.id, next)
        }
    }

    /** Called after an alarm fired for [ruleId]; suppresses one-time rules. */
    fun onFired(ruleId: String, repeats: Boolean) {
        if (!repeats) suppress.add(ruleId)
    }

    private fun schedule(context: Context, ruleId: String, atMs: Long) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        val pendingIntent = intent(context, ruleId)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setWindow(android.app.AlarmManager.RTC_WAKEUP, atMs, 60_000L, pendingIntent)
            } else {
                manager.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, atMs, pendingIntent)
            }
        } catch (e: SecurityException) {
            manager.set(android.app.AlarmManager.RTC_WAKEUP, atMs, pendingIntent)
        }
        Log.d(TAG, "Scheduled rule $ruleId in ${(atMs - System.currentTimeMillis()) / 1000}s")
    }

    private fun cancel(context: Context, ruleId: String) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as android.app.AlarmManager
        manager.cancel(intent(context, ruleId))
    }

    private fun intent(context: Context, ruleId: String): PendingIntent {
        val intent = Intent(context, TimeEventReceiver::class.java)
            .setAction(TimeEventReceiver.ACTION_TIME_EVENT)
            .putExtra(EXTRA_RULE_ID, ruleId)
        return PendingIntent.getBroadcast(
            context,
            ruleId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
