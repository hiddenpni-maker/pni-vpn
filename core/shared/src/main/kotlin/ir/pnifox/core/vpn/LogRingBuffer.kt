package ir.pnifox.core.vpn

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

typealias LogLevel = ir.pnifox.core.model.LogLevel
typealias LogEntry = ir.pnifox.core.model.LogEntry

@Singleton
class LogRingBuffer @Inject constructor() {
    private val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>(MAX_ENTRIES)
    private val _logsFlow = MutableStateFlow<List<LogEntry>>(emptyList())
    val logsFlow: StateFlow<List<LogEntry>> = _logsFlow.asStateFlow()

    fun append(tag: String, level: LogLevel, message: String) {
        val item = LogEntry(
            timestamp = timeFmt.format(Date()),
            tag = tag,
            level = level,
            message = message,
        )
        synchronized(lock) {
            if (entries.size >= MAX_ENTRIES) {
                entries.removeFirst()
            }
            entries.addLast(item)
            _logsFlow.value = entries.toList()
        }
    }

    fun exportPlainText(): String = synchronized(lock) {
        entries.joinToString("\n") { "[${it.timestamp}] ${it.level.name}/${it.tag}: ${it.message}" }
    }

    fun clear() {
        synchronized(lock) {
            entries.clear()
            _logsFlow.value = emptyList()
        }
    }

    private companion object {
        const val MAX_ENTRIES = 400
    }
}
