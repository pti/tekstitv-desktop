package fi.reuna.tekstitv

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import java.awt.Component
import java.awt.EventQueue
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowEvent
import java.awt.event.WindowListener
import java.util.concurrent.Executor
import javax.swing.JFrame
import javax.swing.SwingUtilities


fun Component.observeKeyEvents(): Observable<KeyEvent> = Observable.create<KeyEvent> { emitter ->

    val keyListener = object : KeyListener {

        override fun keyPressed(e: KeyEvent?) { emitter.onNext(e!!) }

        override fun keyReleased(e: KeyEvent?) { emitter.onNext(e!!) }

        override fun keyTyped(e: KeyEvent?) { emitter.onNext(e!!) }
    }

    addKeyListener(keyListener)
    emitter.setCancellable { removeKeyListener(keyListener) }
}

fun JFrame.observeWindowEvents(): Observable<WindowEvent> = Observable.create<WindowEvent> { emitter ->

    val windowListener = object : WindowListener {

        override fun windowActivated(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowClosed(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowClosing(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowDeactivated(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowDeiconified(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowIconified(e: WindowEvent?) { emitter.onNext(e!!) }

        override fun windowOpened(e: WindowEvent?) { emitter.onNext(e!!) }
    }

    addWindowListener(windowListener)
    emitter.setCancellable { removeWindowListener(windowListener) }
}

fun <T> Observable<T>.observeOnEventQueue() = observeOn(eventQueueScheduler)!!

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

operator fun CompositeDisposable.plusAssign(d: Disposable) { add(d) }
