package com.tdbe.playonconnect.data

import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

/**
 * Keeps deleted rules hidden (not actually deleted) for a short undo window.
 * Multiple deletions can be pending at once, each with its own countdown.
 */
object PendingDelete {

    data class Entry(val id: Long, val ruleId: String)

    private const val UNDO_WINDOW_MS = 7_000L

    val entries = MutableStateFlow<List<Entry>>(emptyList())

    private var nextId = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private val runnables = mutableMapOf<Long, Runnable>()

    /** Hides [ruleId] and starts its own undo countdown. */
    suspend fun begin(ruleId: String) {
        RuleStore.setPendingDelete(ruleId, true)
        val entry = Entry(nextId++, ruleId)
        entries.value = entries.value + entry
        val expiry = Runnable {
            runnables.remove(entry.id)
            entries.value = entries.value.filterNot { it.id == entry.id }
            scope.launch { RuleStore.delete(entry.ruleId) }
        }
        runnables[entry.id] = expiry
        handler.postDelayed(expiry, UNDO_WINDOW_MS)
    }

    /** Restores the rule belonging to the entry with [id] and cancels its countdown. */
    suspend fun undo(id: Long) {
        val entry = entries.value.firstOrNull { it.id == id } ?: return
        runnables.remove(entry.id)?.let { handler.removeCallbacks(it) }
        entries.value = entries.value.filterNot { it.id == id }
        RuleStore.setPendingDelete(entry.ruleId, false)
    }
}
