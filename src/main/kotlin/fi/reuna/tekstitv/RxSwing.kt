package fi.reuna.tekstitv

import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.concurrent.Executor
import javax.swing.SwingUtilities


fun Component.observeKeyEvents(): Observable<KeyEvent> = Observable.create<KeyEvent> { emitter ->

    val keyListener = object : KeyListener {

        override fun keyPressed(e: KeyEvent?) {
            emitter.onNext(e!!)
        }

        override fun keyReleased(e: KeyEvent?) {
            emitter.onNext(e!!)
        }

        override fun keyTyped(e: KeyEvent?) {
            emitter.onNext(e!!)
        }
    }

    addKeyListener(keyListener)
    emitter.setCancellable { removeKeyListener(keyListener) }
}

fun <T> Observable<T>.observeOnEventQueue() = this!!.observeOn(eventQueueScheduler)!!

private val eventQueueScheduler = Schedulers.from(SwingScheduler)

private object SwingScheduler : Executor {

    override fun execute(command: Runnable?) {

        if (command == null) {
            return
        }

        if (EventQueue.isDispatchThread()) {
            command.run()
        } else {
            EventQueue.invokeLater(command)
            SwingUtilities.invokeLater(command)
        }
    }
}
