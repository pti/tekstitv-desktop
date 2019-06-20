package fi.reuna.tekstitv

import java.util.*
import kotlin.concurrent.schedule

interface DigitBufferListener {
    fun onDigitBufferChanged(content: String, inputActive: Boolean)
}

class DigitBuffer {

    private val maxDigits = 3
    private val emptyChar = '.'

    private val buffer = StringBuffer()
    private val resetTimer = Timer()
    private var resetTask: TimerTask? = null
    private var lastPageNumber: Int? = null
    private var content: String = updateContent()
    private var inputActive = false

    var listener: DigitBufferListener? = null

    val isEmpty get() = buffer.isEmpty()

    fun close() {
        resetTimer.cancel()
    }

    fun setCurrentPage(number: Int) {

        if (number != lastPageNumber) {
            lastPageNumber = number
            updateContent()
        }
    }

    fun handleInput(digit: Char): Int? {
        stopTimer()
        buffer.append(digit)

        return if (buffer.length == maxDigits) {
            val pageNumber = Integer.parseInt(buffer.toString())
            lastPageNumber = pageNumber
            clear()
            pageNumber

        } else {
            startTimer()
            updateContent()
            null
        }
    }

    fun inputEnded() {

        if (resetTask != null) {
            stopTimer()
            clear()
        }
    }

    private fun startTimer() {
        inputActive = true
        resetTask = resetTimer.schedule(3000) { clear() }
    }

    private fun stopTimer() {
        resetTask?.cancel()
        resetTask = null
        inputActive = false
    }

    private fun clear() {
        stopTimer()
        buffer.delete(0, buffer.length)
        updateContent()
    }

    private fun updateContent(): String {

        content = if (lastPageNumber != null && buffer.isEmpty()) {
            lastPageNumber.toString()
        } else {
            buffer.toString().padEnd(maxDigits, '.')
        }

        listener?.onDigitBufferChanged(content, inputActive)
        return content
    }
}
