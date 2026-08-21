package com.ram.researchdesk

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Port of flutter_app/lib/services/debug_log.dart
 *
 * A process-wide debug log buffer with a broadcast stream, mirroring the
 * Dart singleton `debugLog` + StreamController.broadcast pattern.
 */
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

    private val _stream = MutableSharedFlow<DebugEntry>(
        replay = 0,
        extraBufferCapacity = 256,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Snapshot of all buffered entries (oldest first). */
    val entries: List<DebugEntry>
        get() = synchronized(entriesList) { entriesList.toList() }

    /** Broadcast stream of new entries. */
    val stream: SharedFlow<DebugEntry> = _stream.asSharedFlow()

    fun log(tag: String, message: String) {
        val entry = DebugEntry(tag, message)
        synchronized(entriesList) { entriesList.add(entry) }
        _stream.tryEmit(entry)
    }

    fun clear() {
        synchronized(entriesList) { entriesList.clear() }
    }

    fun export(): String =
        synchronized(entriesList) { entriesList.joinToString("\n") { it.toString() } }
}

/** Global accessor, mirroring `final debugLog = DebugLog()` in Dart. */
val debugLog: DebugLog get() = DebugLog
