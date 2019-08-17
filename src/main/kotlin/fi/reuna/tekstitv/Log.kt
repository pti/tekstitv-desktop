package fi.reuna.tekstitv

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class LogTimeMode {
    DELTA,
    ABSOLUTE
}

class Log {

    private val level = LogLevel.DEBUG
    private val mode = LogTimeMode.ABSOLUTE
    private val t0 = System.nanoTime()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun debug(message: String) {
        log(LogLevel.DEBUG, message)
    }

    private fun info(message: String) {
        log(LogLevel.INFO, message)
    }

    private fun error(message: String, t: Throwable? = null) {
        log(LogLevel.ERROR, message)
        t?.printStackTrace()
    }

    private fun log(level: LogLevel, message: String) {

        if (level.value < this.level.value) {
            return
        }

        val thread = Thread.currentThread()
        val stackTrace = thread.stackTrace
        val se = stackTrace[5] // First elements are Log class and companion object specific ones. #5 is the one that made the log call.
        val className = se.className
        val simpleName = className.substringAfterLast('.')

        when (mode) {
            LogTimeMode.DELTA -> {
                val elapsed = (System.nanoTime() - t0) / 1000000
                println("$elapsed <${level.levelName}> [${thread.name}] $simpleName.${se.methodName}:${se.lineNumber}  $message")
            }
            LogTimeMode.ABSOLUTE -> {
                val timestamp = LocalTime.now().format(formatter)
                println("$timestamp <${level.levelName}> [${thread.name}] $simpleName.${se.methodName}:${se.lineNumber}  $message")
            }
        }
    }

    companion object {

        private val instance = Log()

        fun debug(message: String) {
            instance.debug(message)
        }

        fun info(message: String) {
            instance.info(message)
        }

        fun error(message: String, t: Throwable? = null) {
            instance.error(message, t)
        }
    }
}

enum class LogLevel(val levelName: String, val value: Int) {
    DEBUG("DEBG", 10),
    INFO("INFO", 20),
    ERROR("ERROR", 40)
}
