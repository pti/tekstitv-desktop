package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import fi.reuna.tekstitv.ui.getAppPreferences
import fi.reuna.tekstitv.ui.observeOnEventQueue
import fi.reuna.tekstitv.ui.saveWindowRectangle
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.Duration
import java.util.concurrent.TimeUnit
import javax.swing.JFrame

class Controller(val view: MainView, val frame: JFrame): KeyListener, WindowAdapter() {

    private val provider = PageProvider()
    private val digitBuffer = DigitBuffer()
    private val disposables = CompositeDisposable()
    private var autoRefresher: Disposable? = null
    private val autoRefreshInterval: Duration

    init {
        Log.debug("begin")
        digitBuffer.listener = view.pageNumberView

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

                    if (it is PageEvent.Failed && it.autoReload && view.pagePanel.latestEvent is PageEvent.Loaded) {
                        // Failed to automatically refresh the page -> keep on displaying the currently loaded page
                    } else {
                        view.pagePanel.latestEvent = it
                        view.shortcuts.update(it)

                        if (it is PageEvent.Loaded) {
                            digitBuffer.setCurrentPage(it.subpage.location.page)
                        }
                    }
                }

        // TODO display some kind of loading indicator if request takes a long time

        frame.addKeyListener(this)
        frame.addWindowListener(this)

        startAutoRefresh()
    }

    fun stop() {
        frame.removeKeyListener(this)
        frame.removeWindowListener(this)
        stopAutoRefresh()
        digitBuffer.close()
        NavigationHistory.instance.close()
        disposables.dispose()
        provider.stop()
    }

    private fun setPage(number: Int) {
        val current = provider.currentLocation.page

        if (current != number) {
            NavigationHistory.instance.add(current, number)
        }

        provider.set(number)
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

    override fun keyTyped(e: KeyEvent) {
        val char = e.keyChar

        if (digitBuffer.isEmpty && char == '0') {
            provider.togglePrevious()

        } else if (char.isDigit()) {
            digitBuffer.handleInput(char)?.let { setPage(it) }

        } else {
            view.shortcuts.getShortcut(char)?.let { setPage(it) }
            digitBuffer.inputEnded()
        }
    }

    override fun keyPressed(e: KeyEvent) {

        if (e.isControlDown) {

            when (e.keyCode) {
                KeyEvent.VK_R -> provider.reload()
                KeyEvent.VK_Q -> {
                    stop()
                    frame.dispose()
                }
                else -> return
            }

        } else {

            when (e.keyCode) {
                KeyEvent.VK_LEFT -> provider.prevSubpage()
                KeyEvent.VK_RIGHT -> provider.nextSubpage()
                KeyEvent.VK_UP -> provider.nextPage()
                KeyEvent.VK_DOWN -> provider.prevPage()
                KeyEvent.VK_BACK_SPACE -> provider.back()
                KeyEvent.VK_F5 -> provider.reload()
                else -> return
            }
        }

        digitBuffer.inputEnded()
    }

    override fun keyReleased(e: KeyEvent) {
    }

    override fun windowIconified(e: WindowEvent?) {
        stopAutoRefresh()
    }

    override fun windowDeiconified(e: WindowEvent?) {
        provider.reload()
        startAutoRefresh()
    }

    override fun windowClosing(e: WindowEvent?) {
        frame.saveWindowRectangle(getAppPreferences())
    }
}
