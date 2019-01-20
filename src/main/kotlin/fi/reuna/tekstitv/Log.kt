package fi.reuna.tekstitv

import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class LogTimeMode {
    DELTA,
    ABSOLUTE
}

class Log {

    private val mode = LogTimeMode.DELTA
    private val t0 = System.nanoTime()
    private val formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")

    private fun debug(message: String) {
        log(LogLevel.DEBUG, message)
    }

    private fun info(message: String) {
        log(LogLevel.INFO, message)
    }

    private fun error(message: String) {
        log(LogLevel.ERROR, message)
    }

    private fun log(level: LogLevel, message: String) {
        val thread = Thread.currentThread()
        val stackTrace = thread.stackTrace
        val se = stackTrace[4]
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
            instance.log(LogLevel.DEBUG, message)
        }

        fun info(message: String) {
            instance.log(LogLevel.INFO, message)
        }

        fun error(message: String) {
            instance.log(LogLevel.ERROR, message)
        }
    }
}

enum class LogLevel(val levelName: String) {
    DEBUG("DEBG"),
    INFO("INFO"),
    ERROR("ERROR")
}
