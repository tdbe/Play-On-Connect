package com.tdbe.playonconnect.engine

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.Action
import com.tdbe.playonconnect.data.Rule
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.data.Trigger
import com.tdbe.playonconnect.util.WebApps
import com.tdbe.playonconnect.util.formatMinutes

class RuleEngine(private val context: Context) {

    private enum class Kind { NONE, SOUND, URL }

    private val controller = PlaybackController(context)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())

    // Deduplicate repeated CONNECT broadcasts for the same device that arrive
    // without an intervening DISCONNECT (e.g. ACL + A2DP profile events).
    private var lastConnectedAddress: String? = null
    private var pendingStopToast: String? = null
    private var kind = Kind.NONE
    private var urlTimeoutRunnable: Runnable? = null

    fun handleConnected(address: String) {
        if (lastConnectedAddress?.equals(address, ignoreCase = true) == true) return
        lastConnectedAddress = address
        val rule = RuleStore.rules.value.firstOrNull {
            it.enabled && !it.pendingDelete &&
                it.triggerDeviceAddress()?.equals(address, ignoreCase = true) == true
        } ?: return
        startRule(rule)
    }

    fun handleDisconnected(address: String) {
        if (lastConnectedAddress?.equals(address, ignoreCase = true) == true) lastConnectedAddress = null
        val playing = PlaybackState.current.value ?: return
        val rule = RuleStore.rules.value.firstOrNull { it.id == playing.ruleId } ?: return
        if (rule.triggerDeviceAddress()?.equals(address, ignoreCase = true) == true) {
            val name = (rule.trigger as? Trigger.BluetoothDeviceConnected)?.deviceName ?: address
            pendingStopToast = context.getString(R.string.toast_stopped_disconnected, name)
            stopCurrent()
        }
    }

    fun handleRulesChanged(rules: List<Rule>) {
        val playing = PlaybackState.current.value ?: return
        val rule = rules.firstOrNull { it.id == playing.ruleId }
        if (rule == null || !rule.enabled || rule.pendingDelete) stopCurrent()
    }

    fun stopCurrent() {
        when (kind) {
            Kind.SOUND -> controller.stop()
            Kind.URL -> stopUrlPlayback(toastMessage = true)
            Kind.NONE -> pendingStopToast = null
        }
    }

    fun shutdown() {
        controller.stop()
        cancelUrlTimeout()
        kind = Kind.NONE
        pendingStopToast = null
        PlaybackState.current.value = null
    }

    private fun startRule(rule: Rule) {
        stopCurrent()
        when (val action = rule.action) {
            is Action.PlaySound -> startSound(rule, action)
            is Action.OpenUrl -> startUrl(rule, action)
        }
    }

    private fun startSound(rule: Rule, action: Action.PlaySound) {
        val label = soundLabel(action)
        kind = Kind.SOUND
        PlaybackState.current.value = PlaybackState.Playing(rule.id, label)
        Log.d(TAG, "Rule ${rule.id} triggered: playing $label")
        toast(context.getString(R.string.toast_playing, label))
        controller.start(action) { error, timedOut ->
            if (PlaybackState.current.value?.ruleId == rule.id) {
                PlaybackState.current.value = null
                kind = Kind.NONE
            }
            val message = when {
                error != null -> error
                timedOut -> context.getString(
                    R.string.toast_stopped_timeout,
                    formatMinutes(action.maxMinutes ?: 0.0),
                )
                else -> pendingStopToast ?: context.getString(R.string.toast_stopped)
            }
            pendingStopToast = null
            toast(message)
        }
    }

    private fun startUrl(rule: Rule, action: Action.OpenUrl) {
        kind = Kind.URL
        PlaybackState.current.value = PlaybackState.Playing(rule.id, action.url)
        Log.d(TAG, "Rule ${rule.id} triggered: opening ${action.url}")
        toast(context.getString(R.string.toast_opening_url, action.url))
        val pkg = (if (action.useDedicatedApp) action.appPackage else action.browserPackage)
            ?.takeIf { it.isNotBlank() }
        val opened = WebApps.open(context, action.url, pkg)
        if (!opened) {
            toast(context.getString(R.string.error_couldnt_open_url))
            PlaybackState.current.value = null
            kind = Kind.NONE
            return
        }
        action.maxMinutes?.let { minutes ->
            urlTimeoutRunnable = Runnable {
                pendingStopToast = context.getString(R.string.toast_stopped_timeout, formatMinutes(minutes))
                stopUrlPlayback(toastMessage = true)
            }.also { handler.postDelayed(it, (minutes * 60_000).toLong()) }
        }
    }

    private fun stopUrlPlayback(toastMessage: Boolean) {
        cancelUrlTimeout()
        dispatchMediaPause()
        val message = pendingStopToast ?: context.getString(R.string.toast_stopped)
        pendingStopToast = null
        PlaybackState.current.value = null
        kind = Kind.NONE
        if (toastMessage) toast(message)
    }

    /** Sends a media-pause key event so whatever is playing (e.g. in a browser) pauses. */
    private fun dispatchMediaPause() {
        runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    private fun cancelUrlTimeout() {
        urlTimeoutRunnable?.let { handler.removeCallbacks(it) }
        urlTimeoutRunnable = null
    }

    private fun soundLabel(action: Action.PlaySound): String {
        val names = action.effectiveNames
        return when {
            names.size == 1 -> names[0]
            names.size > 1 -> context.getString(R.string.summary_files_count, action.effectiveUris.size)
            else -> context.getString(R.string.label_sound)
        }
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun Rule.triggerDeviceAddress(): String? =
        (trigger as? Trigger.BluetoothDeviceConnected)?.deviceAddress

    private companion object {
        const val TAG = "RuleEngine"
    }
}
