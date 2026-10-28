package com.tdbe.playonconnect.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.tdbe.playonconnect.MainActivity
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.engine.BluetoothMonitor
import com.tdbe.playonconnect.engine.PlaybackState
import com.tdbe.playonconnect.engine.RuleEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class PlaybackService : Service() {

    companion object {
        private const val CHANNEL_ID = "playonconnect_service"
        private const val NOTIFICATION_ID = 1

        const val ACTION_START = "com.tdbe.playonconnect.action.START"
        const val ACTION_STOP_PLAYBACK = "com.tdbe.playonconnect.action.STOP_PLAYBACK"

        private const val EXTRA_ADDRESS = "extra_address"
        private const val EXTRA_CONNECTED = "extra_connected"

        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java).setAction(ACTION_START)
                )
            }
        }

        // Used by the manifest-declared fallback receiver: wakes/keeps the
        // service running and forwards a connection event in case the
        // runtime-registered receiver missed it.
        fun notifyConnection(context: Context, address: String, connected: Boolean) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, PlaybackService::class.java)
                        .setAction(ACTION_START)
                        .putExtra(EXTRA_ADDRESS, address)
                        .putExtra(EXTRA_CONNECTED, connected)
                )
            }
        }

        fun stopPlayback(context: Context) {
            if (!isRunning) return
            context.startService(
                Intent(context, PlaybackService::class.java).setAction(ACTION_STOP_PLAYBACK)
            )
        }
    }

    private lateinit var engine: RuleEngine
    private lateinit var monitor: BluetoothMonitor
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var commandSeen = false

    private fun List<com.tdbe.playonconnect.data.Rule>.activeCount(): Int =
        count { it.enabled && !it.pendingDelete }

    override fun onCreate() {
        super.onCreate()
        RuleStore.init(this)
        createChannel()
        engine = RuleEngine(this)
        monitor = BluetoothMonitor(this)
        monitor.start { address, connected ->
            if (connected) engine.handleConnected(address) else engine.handleDisconnected(address)
        }
        isRunning = true
        scope.launch {
            combine(RuleStore.rules, PlaybackState.current) { rules, playing -> rules to playing }
                .collect { (rules, playing) ->
                    engine.handleRulesChanged(rules)
                    val enabledCount = rules.activeCount()
                    if (enabledCount == 0) {
                        if (commandSeen) {
                            stopForeground(STOP_FOREGROUND_REMOVE)
                            stopSelf()
                        }
                    } else {
                        NotificationManagerCompat.from(this@PlaybackService)
                            .notify(NOTIFICATION_ID, buildNotification(rules, playing))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        commandSeen = true
        startForeground(NOTIFICATION_ID, buildNotification(RuleStore.rules.value, PlaybackState.current.value))
        if (intent?.action == ACTION_STOP_PLAYBACK) engine.stopCurrent()
        val forwardedAddress = intent?.getStringExtra(EXTRA_ADDRESS)
        if (forwardedAddress != null && intent.hasExtra(EXTRA_CONNECTED)) {
            if (intent.getBooleanExtra(EXTRA_CONNECTED, false)) {
                engine.handleConnected(forwardedAddress)
            } else {
                engine.handleDisconnected(forwardedAddress)
            }
        }
        if (RuleStore.rules.value.activeCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        monitor.stop()
        engine.shutdown()
        isRunning = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = getString(R.string.notification_channel_desc) }
        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun buildNotification(
        rules: List<com.tdbe.playonconnect.data.Rule>,
        playing: PlaybackState.Playing?,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
        if (playing != null) {
            builder.setContentText(getString(R.string.notification_playing, playing.label))
            val stopIntent = PendingIntent.getService(
                this, 1,
                Intent(this, PlaybackService::class.java).setAction(ACTION_STOP_PLAYBACK),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.addAction(0, getString(R.string.action_stop), stopIntent)
        } else {
            val count = rules.activeCount()
            builder.setContentText(resources.getQuantityString(R.plurals.notification_listening, count, count))
        }
        return builder.build()
    }
}
