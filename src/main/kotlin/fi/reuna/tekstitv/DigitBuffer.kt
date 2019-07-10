package fi.reuna.tekstitv

import java.time.Duration

interface DigitBufferListener {
    fun onDigitBufferChanged(content: String, inputActive: Boolean)
}

class DigitBuffer {

    private val maxDigits = 3
    private val emptyChar = '.'

    private val buffer = StringBuffer()
    private val resetter = Debouncer()
    private var lastPageNumber: Int? = null
    private var content: String = updateContent()
    private var inputActive = false

    var listener: DigitBufferListener? = null

    val isEmpty get() = buffer.isEmpty()

    fun close() {
        resetter.destroy()
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
        stopTimer()
        clear()
    }

    private fun startTimer() {
        inputActive = true
        resetter.start(Duration.ofSeconds(3)) { clear() }
    }

    private fun stopTimer() {
        resetter.stop()
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
            buffer.toString().padEnd(maxDigits, emptyChar)
        }

        listener?.onDigitBufferChanged(content, inputActive)
        return content
    }
}
