package org.mountaincircles.app.logger

enum class LogLevel { DEBUG, INFO, WARN, ERROR }
object LogConfig {
    private var loggingMode = LoggingMode.NONE
    val enabledLogs = mutableMapOf<String, LogLevel?>()

    enum class LoggingMode {
        NONE,    // Disable all logs
        ALL,     // Enable all logs
        SELECTIVE // Enable only configured logs
    }

    // Production: disable all logs
    fun enableNone() {
        loggingMode = LoggingMode.NONE
        enabledLogs.clear()
    }

    // Development: enable all logs
    fun enableAll() {
        loggingMode = LoggingMode.ALL
        enabledLogs.clear()
    }

    // Enable selective logging
    fun enableSelective(config: Map<String, LogLevel>) {
        loggingMode = LoggingMode.SELECTIVE
        enabledLogs.clear()
        enabledLogs.putAll(config)
    }

    // Check if a log should be displayed
    fun shouldLog(tag: String, level: LogLevel): Boolean {
        return when (loggingMode) {
            LoggingMode.NONE -> false  // No logs in production
            LoggingMode.ALL -> true    // All logs in development
            LoggingMode.SELECTIVE -> {
                val configuredLevel = enabledLogs[tag] ?: return false
                configuredLevel != null && level.ordinal >= configuredLevel.ordinal
            }
        }
    }

    // Initialization
    fun initialize() {
        // Default to all logs (so widget/background paths log without MainActivity)
        enableAll()
    }
}

object Logger {
    init {
        LogConfig.initialize()
    }

    fun log(tag: String, level: LogLevel, message: String, throwable: Throwable? = null) {
        if (!LogConfig.shouldLog(tag, level)) {
            return
        }

        val prefix = when (level) {
            LogLevel.DEBUG -> "DEBUG"
            LogLevel.INFO -> "INFO"
            LogLevel.WARN -> "WARN"
            LogLevel.ERROR -> "ERROR"
            else -> "UNKNOWN"
        }
        println("$prefix [MC_$tag] $message${throwable?.let { " - ${it.message}" } ?: ""}")
        throwable?.printStackTrace()
    }

    // Convenience methods
    fun debug(tag: String, message: String) = log(tag, LogLevel.DEBUG, message)
    fun info(tag: String, message: String) = log(tag, LogLevel.INFO, message)
    fun warn(tag: String, message: String) = log(tag, LogLevel.WARN, message)
    fun error(tag: String, message: String, throwable: Throwable? = null) = log(tag, LogLevel.ERROR, message, throwable)
}
