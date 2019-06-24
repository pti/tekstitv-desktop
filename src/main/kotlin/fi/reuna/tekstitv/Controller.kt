package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import fi.reuna.tekstitv.ui.getAppPreferences
import fi.reuna.tekstitv.ui.saveWindowRectangle
import kotlinx.coroutines.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.time.Duration
import javax.swing.JFrame

class Controller(val view: MainView, val frame: JFrame): KeyListener, WindowAdapter(), PageEventListener {

    private val provider = PageProvider(this)
    private val digitBuffer = DigitBuffer()
    private var autoRefreshJob: Job? = null
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

        // TODO display some kind of loading indicator if request takes a long time

        frame.addKeyListener(this)
        frame.addWindowListener(this)
    }

    fun stop() {
        frame.removeKeyListener(this)
        frame.removeWindowListener(this)
        stopAutoRefresh()
        digitBuffer.close()
        NavigationHistory.instance.close()
        provider.stop()
    }

    private fun setPage(number: Int) {
        val current = provider.currentLocation.page

        if (current != number) {
            NavigationHistory.instance.add(current, number)
        }

        provider.set(number)
    }

    private fun restartAutoRefresh() {
        autoRefreshJob?.cancel()

        autoRefreshJob = GlobalScope.launch(Dispatchers.Main) {
            delay(autoRefreshInterval.toMillis())
            provider.reload(autoReload = true)
        }
    }

    private fun stopAutoRefresh() {
        autoRefreshJob?.cancel()
        autoRefreshJob = null
    }

    override fun onPageEvent(event: PageEvent) {
        restartAutoRefresh()
        
        if (event is PageEvent.Failed && event.autoReload && view.pagePanel.latestEvent is PageEvent.Loaded) {
            // Failed to automatically refresh the page -> keep on displaying the currently loaded page
        } else {
            view.pagePanel.latestEvent = event
            view.shortcuts.update(event)

            if (event is PageEvent.Loaded) {
                digitBuffer.setCurrentPage(event.subpage.location.page)
            }
        }
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
    }

    override fun windowClosing(e: WindowEvent?) {
        frame.saveWindowRectangle(getAppPreferences())
    }
}
