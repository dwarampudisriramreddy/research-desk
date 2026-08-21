package com.ram.researchdesk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DebugEntry(
    val tag: String,
    val message: String,
    val time: Date = Date(),
) {
    override fun toString(): String {
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return "${fmt.format(time)} [$tag] $message"
    }
}

object DebugLog {

    private val entriesList = mutableListOf<DebugEntry>()
    private val pendingBatch = mutableListOf<DebugEntry>()
    private val batchScope = CoroutineScope(Dispatchers.Default)

    private val _batchStream = MutableSharedFlow<List<DebugEntry>>(
        replay = 0,
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val entries: List<DebugEntry>
        get() = synchronized(entriesList) { entriesList.toList() }

    /** Emits batches of entries every ~300ms instead of every single entry. */
    val batchStream: SharedFlow<List<DebugEntry>> = _batchStream.asSharedFlow()

    init {
        batchScope.launch {
            while (true) {
                delay(300)
                val batch: List<DebugEntry>
                synchronized(pendingBatch) {
                    if (pendingBatch.isEmpty()) return@launch
                    batch = pendingBatch.toList()
                    pendingBatch.clear()
                }
                _batchStream.tryEmit(batch)
            }
        }
    }

    fun log(tag: String, message: String) {
        val entry = DebugEntry(tag, message)
        synchronized(entriesList) { entriesList.add(entry) }
        synchronized(pendingBatch) { pendingBatch.add(entry) }
    }

    fun clear() {
        synchronized(entriesList) { entriesList.clear() }
        synchronized(pendingBatch) { pendingBatch.clear() }
    }

    fun export(): String =
        synchronized(entriesList) { entriesList.joinToString("\n") { it.toString() } }
}

val debugLog: DebugLog get() = DebugLog
