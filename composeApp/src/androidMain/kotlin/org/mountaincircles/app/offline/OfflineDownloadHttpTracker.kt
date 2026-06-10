package org.mountaincircles.app.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-app HTTP trace for offline pack downloads (no logcat required).
 * Only records while [isActive] is true.
 */
object OfflineDownloadHttpTracker {
    private const val MAX_LINES = 150

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    @Volatile
    var isActive: Boolean = false
        private set

    fun beginSession() {
        installMapLibreHttpDebugClientIfNeeded()
        _lines.value = emptyList()
        isActive = true
        append("— offline download HTTP trace started —")
    }

    fun endSession() {
        if (isActive) {
            append("— offline download HTTP trace ended —")
        }
        isActive = false
    }

    fun logRequest(url: String) {
        if (!isActive) return
        append("→ ${requestSummary(url)}")
    }

    fun logResponse(url: String, statusCode: Int, statusMessage: String, contentLength: Long?) {
        if (!isActive) return
        val size = contentLength?.takeIf { it >= 0 }?.let { " ${it}B" } ?: ""
        append("← $statusCode $statusMessage$size  ${requestSummary(url)}")
    }

    fun logFailure(url: String, statusCode: Int, message: String) {
        if (!isActive) return
        append("✗ $statusCode $message  ${requestSummary(url)}")
    }

    private fun append(line: String) {
        _lines.update { current -> (current + line).takeLast(MAX_LINES) }
    }

    private fun requestSummary(url: String): String {
        if (url.length <= 96) return url
        return url.take(48) + "…" + url.takeLast(44)
    }
}
