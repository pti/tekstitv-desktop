package fi.reuna.tekstitv

import java.time.Duration
import java.util.*
import javax.swing.SwingUtilities
import kotlin.concurrent.schedule

class Debouncer {

    private var timer: Timer? = null
    private var task: TimerTask? = null

    fun destroy() {
        timer?.cancel()
    }

    fun start(delay: Duration, action: () -> Unit) {
        stop()
        if (timer == null) timer = Timer()
        task = timer!!.schedule(delay.toMillis()) { SwingUtilities.invokeLater(action) }
    }

    fun stop() {
        task?.cancel()
        task = null
    }
}
