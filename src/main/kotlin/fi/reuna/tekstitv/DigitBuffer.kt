package fi.reuna.tekstitv

import java.util.*
import kotlin.concurrent.schedule

class DigitBuffer {

    private val buffer = StringBuffer()
    private val resetTimer = Timer()
    private var resetTask: TimerTask? = null

    val isEmpty get() = buffer.isEmpty()

    fun handleInput(digit: Char): Int? {
        stopTimer()
        buffer.append(digit)

        return if (buffer.length == 3) {
            val pageNumber = Integer.parseInt(buffer.toString())
            clear()
            pageNumber

        } else {
            startTimer()
            null
        }
    }

    fun close() {
        resetTimer.cancel()
    }

    fun inputEnded() {
        stopTimer()
        clear()
    }

    private fun startTimer() {
        // TODO some coroutine stuff instead of a timer?
        resetTask = resetTimer.schedule(3000) { clear() }
    }

    private fun stopTimer() {
        resetTask?.cancel()
    }

    private fun clear() {
        buffer.delete(0, buffer.length)
    }
}
