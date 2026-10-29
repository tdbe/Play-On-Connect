package com.tdbe.playonconnect.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.tdbe.playonconnect.R
import com.tdbe.playonconnect.data.Action
import com.tdbe.playonconnect.data.AppSettings
import com.tdbe.playonconnect.data.Rule
import com.tdbe.playonconnect.data.RuleStore
import com.tdbe.playonconnect.data.Trigger
import com.tdbe.playonconnect.util.TimeSpec
import com.tdbe.playonconnect.util.WebApps
import com.tdbe.playonconnect.util.formatMinutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Runs rules. Rules in the same trigger group (same Bluetooth device, or all
 * time events) play in parallel with their own player instances; starting a
 * rule from one group stops everything playing from other groups.
 */
class RuleEngine(private val context: Context) {

    private val BT_START_DELAY_MS = 3000L

    enum class Kind { SOUND, URL }

    private class Entry(
        val ruleId: String,
        val groupKey: String,
        val kind: Kind,
        val label: String,
        val maxMinutes: Double?,
        val controller: PlaybackController?,
        var timeoutRunnable: Runnable?,
        var stopMessage: String? = null,
    )

    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val entries = mutableMapOf<String, Entry>()

    // Deduplicate repeated CONNECT broadcasts for the same device that arrive
    // without an intervening DISCONNECT (e.g. ACL + A2DP profile events).
    private var lastConnectedAddress: String? = null

    // Delayed audio starts for Bluetooth-triggered rules (gives the speaker a
    // moment to become ready), keyed by rule id.
    private val pendingBtStarts = mutableMapOf<String, Runnable>()

    private var focusRequest: AudioFocusRequest? = null
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            stopAllSounds()
        }
    }

    fun isPlaying(ruleId: String): Boolean = entries.containsKey(ruleId)

    fun handleConnected(address: String) {
        if (lastConnectedAddress?.equals(address, ignoreCase = true) == true) return
        lastConnectedAddress = address
        val group = btGroup(address)
        val matches = RuleStore.rules.value.filter {
            it.enabled && !it.pendingDelete && it.triggerDeviceAddress()?.equals(address, ignoreCase = true) == true
        }
        if (matches.isEmpty()) return
        stopOtherGroups(group)
        matches.forEach { scheduleBtStart(it) }
    }

    private fun scheduleBtStart(rule: Rule) {
        cancelPendingBtStart(rule.id)
        val runnable = Runnable {
            pendingBtStarts.remove(rule.id)
            val fresh = RuleStore.rules.value.firstOrNull { it.id == rule.id }
            if (fresh != null && fresh.enabled && !fresh.pendingDelete && !entries.containsKey(rule.id)) {
                startEntry(fresh)
            }
        }
        pendingBtStarts[rule.id] = runnable
        handler.postDelayed(runnable, BT_START_DELAY_MS)
    }

    private fun cancelPendingBtStart(ruleId: String) {
        pendingBtStarts.remove(ruleId)?.let { handler.removeCallbacks(it) }
    }

    fun handleDisconnected(address: String) {
        if (lastConnectedAddress?.equals(address, ignoreCase = true) == true) lastConnectedAddress = null
        val group = btGroup(address)
        val affected = entries.values.filter { it.groupKey == group }
        if (affected.isEmpty()) return
        val name = affected.firstNotNullFoundDeviceName() ?: address
        val message = context.getString(R.string.toast_stopped_disconnected, name)
        affected.forEach { stopEntry(it, message) }
    }

    fun handleTimeEvent(ruleId: String) {
        val rule = RuleStore.rules.value.firstOrNull {
            it.id == ruleId && it.enabled && !it.pendingDelete && it.trigger is Trigger.TimeEvent
        } ?: return
        stopOtherGroups(TIME_GROUP)
        startEntry(rule)
    }

    fun handleRulesChanged(rules: List<Rule>) {
        entries.values.toList().forEach { entry ->
            val rule = rules.firstOrNull { it.id == entry.ruleId }
            if (rule == null || !rule.enabled || rule.pendingDelete) {
                stopEntry(entry, context.getString(R.string.toast_stopped))
            }
        }
    }

    fun stopRule(ruleId: String) {
        entries[ruleId]?.let { stopEntry(it, context.getString(R.string.toast_stopped)) }
    }

    fun stopAll() {
        entries.values.toList().forEach { stopEntry(it, context.getString(R.string.toast_stopped)) }
    }

    private fun stopAllSounds() {
        entries.values.filter { it.kind == Kind.SOUND }.toList().forEach {
            stopEntry(it, context.getString(R.string.toast_stopped))
        }
    }

    fun shutdown() {
        entries.values.toList().forEach { stopEntry(it, null) }
        entries.clear()
        syncPlaying()
        abandonAudioFocus()
    }

    // ---------------------------------------------------------------- start

    private fun startEntry(rule: Rule) {
        when (val action = rule.action) {
            is Action.PlaySound -> startSound(rule, action)
            is Action.OpenUrl -> startUrl(rule, action)
        }
    }

    private fun startSound(rule: Rule, action: Action.PlaySound) {
        val label = soundLabel(action)
        val controller = PlaybackController(context)
        val entry = Entry(
            ruleId = rule.id,
            groupKey = groupKeyOf(rule),
            kind = Kind.SOUND,
            label = label,
            maxMinutes = action.maxMinutes,
            controller = controller,
            timeoutRunnable = null,
        )
        entries[rule.id] = entry
        syncPlaying()
        acquireAudioFocusIfNeeded()
        Log.d(TAG, "Rule ${rule.id} triggered: playing $label")
        toast(context.getString(R.string.toast_playing, label))
        controller.start(action) { error, timedOut ->
            val current = entries.remove(rule.id) ?: return@start
            val message = when {
                error != null -> error
                timedOut -> timeoutMessage(current)
                else -> current.stopMessage ?: context.getString(R.string.toast_stopped)
            }
            syncPlaying()
            abandonAudioFocusIfIdle()
            toast(message)
            disableIfOneTime(rule)
        }
    }

    private fun startUrl(rule: Rule, action: Action.OpenUrl) {
        val entry = Entry(
            ruleId = rule.id,
            groupKey = groupKeyOf(rule),
            kind = Kind.URL,
            label = action.url,
            maxMinutes = action.maxMinutes,
            controller = null,
            timeoutRunnable = null,
        )
        entries[rule.id] = entry
        syncPlaying()
        Log.d(TAG, "Rule ${rule.id} triggered: opening ${action.url}")
        toast(context.getString(R.string.toast_opening_url, action.url))
        val pkg = (if (action.useDedicatedApp) action.appPackage else action.browserPackage)
            ?.takeIf { it.isNotBlank() }
        val opened = WebApps.open(context, action.url, pkg)
        if (!opened) {
            entries.remove(rule.id)
            syncPlaying()
            toast(context.getString(R.string.error_couldnt_open_url))
            disableIfOneTime(rule)
            return
        }
        action.maxMinutes?.let { minutes ->
            entry.timeoutRunnable = Runnable {
                stopEntry(entry, timeoutMessage(entry))
            }.also { handler.postDelayed(it, (minutes * 60_000).toLong()) }
        }
    }

    private fun stopEntry(entry: Entry, message: String?) {
        when (entry.kind) {
            Kind.SOUND -> {
                cancelPendingBtStart(entry.ruleId)
                entry.stopMessage = message ?: context.getString(R.string.toast_stopped)
                entry.controller?.stop() // finish callback removes the entry
            }

            Kind.URL -> {
                if (entries.remove(entry.ruleId) == null) return
                entry.timeoutRunnable?.let { handler.removeCallbacks(it) }
                dispatchMediaPause()
                syncPlaying()
                val rule = RuleStore.rules.value.firstOrNull { it.id == entry.ruleId }
                if (message != null) toast(message)
                if (rule != null) disableIfOneTime(rule)
            }
        }
    }

    /** Disables one-time (non-repeating) time-event rules once they have stopped. */
    private fun disableIfOneTime(rule: Rule) {
        val trigger = rule.trigger as? Trigger.TimeEvent ?: return
        if (TimeSpec.repeats(trigger.spec)) return
        scope.launch { RuleStore.setEnabled(rule.id, false) }
    }

    // -------------------------------------------------------------- groups

    private fun stopOtherGroups(group: String) {
        entries.values.filter { it.groupKey != group }.toList().forEach {
            stopEntry(it, context.getString(R.string.toast_stopped))
        }
    }

    private fun groupKeyOf(rule: Rule): String = when (val trigger = rule.trigger) {
        is Trigger.BluetoothDeviceConnected -> btGroup(trigger.deviceAddress)
        is Trigger.TimeEvent -> TIME_GROUP
    }

    private fun btGroup(address: String) = "bt:${address.lowercase()}"

    private fun List<Entry>.firstNotNullFoundDeviceName(): String? {
        for (entry in this) {
            val rule = RuleStore.rules.value.firstOrNull { it.id == entry.ruleId }
            val name = (rule?.trigger as? Trigger.BluetoothDeviceConnected)?.deviceName
            if (name != null) return name
        }
        return null
    }

    // ---------------------------------------------------------------- focus

    private fun acquireAudioFocusIfNeeded() {
        if (AppSettings.playInParallel) return
        if (focusRequest != null) return
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener(focusListener)
            .build()
        runCatching { audioManager.requestAudioFocus(request) }
        focusRequest = request
    }

    private fun abandonAudioFocusIfIdle() {
        if (entries.values.any { it.kind == Kind.SOUND }) return
        abandonAudioFocus()
    }

    private fun abandonAudioFocus() {
        focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        focusRequest = null
    }

    // ---------------------------------------------------------------- misc

    private fun dispatchMediaPause() {
        runCatching {
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE))
            audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PAUSE))
        }
    }

    private fun timeoutMessage(entry: Entry): String =
        context.getString(R.string.toast_stopped_timeout, formatMinutes(entry.maxMinutes ?: 0.0))

    private fun syncPlaying() {
        PlaybackState.playing.value = entries.values.associate { it.ruleId to it.label }
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
        const val TIME_GROUP = "time"
    }
}
