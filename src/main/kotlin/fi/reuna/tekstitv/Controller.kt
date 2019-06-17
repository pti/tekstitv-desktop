package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.*
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.awt.event.KeyEvent
import java.awt.event.WindowEvent
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

class Controller(panel: SubpagePanel, frame: JFrame) {

    private val provider = PageProvider()
    private val digitBuffer = DigitBuffer()
    private val disposables = CompositeDisposable()
    private var autoRefresher: Disposable? = null
    private val autoRefreshInterval: Duration

    init {
        Log.debug("begin")

        val cfg = ConfigurationProvider.cfg
        autoRefreshInterval = cfg.autoRefreshInterval
        Log.debug("got configuration")

        NavigationHistory.instance.load()
        Log.debug("navigation history loaded")

        provider.set(cfg.startPage)
        Log.debug("set initial page")

        provider.observe()
                .observeOnEventQueue()
                .subscribe {

                    if (it is PageEvent.Failed && it.autoReload && panel.latestEvent is PageEvent.Loaded) {
                        // Failed to automatically refresh the page -> keep on displaying the currently loaded page
                    } else {
                        panel.latestEvent = it
                    }
                }

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
                        digitBuffer.handleInput(char)?.let { setPage(it) }

                    } else {
                        // TODO
                        when (char) {
                            'r' -> setPage(100)
                            'g' -> setPage(235)
                            'y' -> setPage(300)
                            'b' -> setPage(400)
                        }

                        digitBuffer.inputEnded()
                    }
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

    private fun setPage(number: Int) {
        val current = provider.currentLocation.page

        if (current != number) {
            NavigationHistory.instance.add(current, number)
        }

        provider.set(number)
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
                .debounce(autoRefreshInterval.seconds, TimeUnit.SECONDS)
                .subscribe { provider.reload(autoReload = true) }
    }

    private fun stopAutoRefresh() {
        autoRefresher?.dispose()
        autoRefresher = null
    }
}
