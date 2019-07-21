package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import fi.reuna.tekstitv.ui.PageLinkListener
import fi.reuna.tekstitv.ui.saveWindowRectangle
import java.awt.event.*
import java.time.Duration
import javax.swing.JFrame

class Controller(private val view: MainView, private val frame: JFrame): KeyListener, WindowAdapter(), PageEventListener, PageLinkListener {

    private val provider = PageProvider(this)
    private val digitBuffer = DigitBuffer()
    private val autoRefresher = Debouncer()
    private val autoRefreshInterval: Duration

    init {
        Log.debug("begin")
        digitBuffer.listener = view.pageNumberView

        val cfg = Configuration.instance
        autoRefreshInterval = cfg.autoRefreshInterval
        Log.debug("got configuration")

        provider.set(cfg.startPage)
        Log.debug("set initial page")

        // TODO display some kind of loading indicator if request takes a long time

        frame.addKeyListener(this)
        frame.addWindowListener(this)
        view.pagePanel.pageLinkListener = this
    }

    fun stop() {
        frame.removeKeyListener(this)
        frame.removeWindowListener(this)
        view.pagePanel.stop()
        autoRefresher.destroy()
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

        autoRefresher.start(autoRefreshInterval) {
            provider.refresh()
        }
    }

    private fun stopAutoRefresh() {
        autoRefresher.stop()
    }

    override fun onPageEvent(event: PageEvent) {
        restartAutoRefresh()
        
        if (event is PageEvent.Failed && event.autoReload && view.pagePanel.latestEvent is PageEvent.Loaded) {
            // Failed to automatically refresh the page -> keep on displaying the currently loaded page

        } else if (event !is PageEvent.Loaded || !event.noChange) {
            // noChange is used to avoid repainting and to inform that another auto-refresh round can be starter
            // (better to wait until the refresh request has ended instead of having a fixed repeating timer that
            // ignores communication delays and thus in worst cases posts another request while the previous was
            // still active).
            view.pagePanel.latestEvent = event
            view.shortcuts.update(event)

            val location = (event as? PageEvent.Loaded)?.location ?: (event as? PageEvent.Failed)?.location
            location?.let { digitBuffer.setCurrentPage(it.page) }
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
                KeyEvent.VK_R -> provider.refresh()
                KeyEvent.VK_Q -> {
                    frame.saveWindowRectangle()
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
                KeyEvent.VK_F5 -> provider.refresh()
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
        provider.refresh()
    }

    override fun windowClosing(e: WindowEvent?) {
        frame.saveWindowRectangle()
    }

    override fun onPageLinkClicked(link: PageLink) {
        provider.set(link.page)
    }
}
