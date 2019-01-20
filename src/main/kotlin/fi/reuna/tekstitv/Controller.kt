package fi.reuna.tekstitv

import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

class Controller(panel: SubpagePanel, frame: JFrame) {

    private val provider = PageProvider()
    private val digitBuffer = DigitBuffer()
    private val disposables = CompositeDisposable()
    private var autoRefresher: Disposable? = null

    init {
        Log.debug("begin")
        provider.set(100) // TODO initial page defined in config
        Log.debug("set initial page")

        provider.observe()
                .observeOnEventQueue()
                .subscribe { panel.latestEvent = it }

        disposables += frame.observeKeyEvents()
                .filter { it.id == KeyEvent.KEY_PRESSED }
                .observeOnEventQueue()
                .subscribe { e ->

                    if (e.isControlDown) {

                        when (e.keyCode) {
                            KeyEvent.VK_R -> provider.reload()
                            KeyEvent.VK_Q -> {
                                stop()
                                (SwingUtilities.getRoot(panel) as? JFrame)?.dispose()
                            }
                            else -> return@subscribe
                        }

                    } else {

                        when (e.keyCode) {
                            KeyEvent.VK_LEFT -> provider.prevSubpage()
                            KeyEvent.VK_RIGHT -> provider.nextSubpage()
                            KeyEvent.VK_UP -> provider.nextPage()
                            KeyEvent.VK_DOWN -> provider.prevPage()
                            KeyEvent.VK_BACK_SPACE -> provider.back()
                            KeyEvent.VK_F5 -> provider.reload()
                            else -> return@subscribe
                        }
                    }

                    digitBuffer.inputEnded()
                }

        // TODO display some kind of loading indicator if request takes a long time
        // TODO display and update page number when consuming key events -> observable for digitbuffer / provide property

        disposables += frame.observeKeyEvents()
                .observeOnEventQueue()
                .filter { it.id == KeyEvent.KEY_TYPED }
                .subscribe { e ->
                    val char = e.keyChar

                    if (digitBuffer.isEmpty && char == '0') {
                        provider.togglePrevious()

                    } else if (char.isDigit()) {
                        digitBuffer.handleInput(char)?.let { provider.set(it) }
                    }
                    // TODO handle r g y b -> shortcuts to "favorites" -- tee joku favmanager joka käsittelee valinnan sivukohtaisesti
                }

        startAutoRefresh()

        frame.observeWindowEvents()
                .subscribe {
                    when (it.id) {
                        WindowEvent.WINDOW_ICONIFIED -> stopAutoRefresh()
                        WindowEvent.WINDOW_DEICONIFIED -> {
                            provider.reload()
                            startAutoRefresh()
                        }
                    }
                }
    }

    fun stop() {
        stopAutoRefresh()
        digitBuffer.close()
        disposables.dispose()
        provider.stop()
    }

    private fun startAutoRefresh() {
        if (autoRefresher != null) return

        autoRefresher = provider.observe()
                .debounce(1, TimeUnit.MINUTES)
                .subscribe { provider.reload() }
    }

    private fun stopAutoRefresh() {
        autoRefresher?.dispose()
        autoRefresher = null
    }
}
