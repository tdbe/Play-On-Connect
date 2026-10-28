package com.tdbe.playonconnect.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class RuleFile(val rules: List<Rule> = emptyList())

object RuleStore {
    private const val FILE_NAME = "rules.json"

    private val json = Json { ignoreUnknownKeys = true }
    private val mutex = Mutex()
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var file: File? = null

    private val _rules = MutableStateFlow<List<Rule>>(emptyList())
    val rules: StateFlow<List<Rule>> = _rules.asStateFlow()

    fun init(context: Context) {
        if (file != null) return
        synchronized(this) {
            if (file != null) return
            val f = File(context.applicationContext.filesDir, FILE_NAME)
            file = f
            _rules.value = runCatching {
                if (f.exists()) {
                    // Anything left pending-delete from a previous session is considered gone.
                    json.decodeFromString<RuleFile>(f.readText()).rules.filterNot { it.pendingDelete }
                } else {
                    emptyList()
                }
            }.getOrDefault(emptyList())
        }
    }

    suspend fun upsert(rule: Rule) = mutate { list ->
        if (list.any { it.id == rule.id }) list.map { if (it.id == rule.id) rule else it } else list + rule
    }

    /** Inserts [newRule] directly after the rule with [afterId] (or at the end). */
    suspend fun insertAfter(afterId: String, newRule: Rule) = mutate { list ->
        val index = list.indexOfFirst { it.id == afterId }
        if (index == -1) list + newRule else list.subList(0, index + 1) + newRule + list.subList(index + 1, list.size)
    }

    suspend fun delete(id: String) = mutate { list -> list.filterNot { it.id == id } }

    suspend fun setEnabled(id: String, enabled: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(enabled = enabled) else it }
    }

    suspend fun setPendingDelete(id: String, pending: Boolean) = mutate { list ->
        list.map { if (it.id == id) it.copy(pendingDelete = pending) else it }
    }

    private fun mutate(transform: (List<Rule>) -> List<Rule>) {
        _rules.value = transform(_rules.value)
        ioScope.launch {
            mutex.withLock { file?.writeText(json.encodeToString(RuleFile(_rules.value))) }
        }
    }
}
