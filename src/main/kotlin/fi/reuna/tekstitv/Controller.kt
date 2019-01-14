package fi.reuna.tekstitv

import io.reactivex.disposables.CompositeDisposable
import java.awt.event.KeyEvent
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import javax.swing.SwingUtilities

class Controller(private val panel: SubpagePanel, frame: JFrame) {

    private val provider = PageProvider()
    private val digitBuffer = DigitBuffer()
    private val disposables = CompositeDisposable()

    // TODO ^ stop autorefresh when minimized - kts JXTest

    init {
        provider.observe()
                .observeOnEventQueue()
                .subscribe {

                    when (it) {
                        is PageEvent.Loaded -> {
                            Log.debug("got ${it.subpage.location}")
                            panel.subpage = it.subpage
                        }
                        is PageEvent.Failed -> {
                            Log.error("failed to load page ${it.location}: ${it.error}")
                            panel.errorMessage = "failed to load page ${it.location}: ${it.error}"
                        }
                        is PageEvent.NotFound -> {
                            Log.error("page not found: ${it.location}")
                            panel.errorMessage = "page not found: ${it.location}"
                        }
                    }
                }

        disposables += provider.observe()
                .debounce(1, TimeUnit.MINUTES)
                .subscribe { provider.reload() }

        Log.debug("provider observer set up")
        provider.set(100) // TODO initial page defined in config

        val frame = SwingUtilities.getRoot(panel) as JFrame

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
    }

    fun stop() {
        digitBuffer.close()
        disposables.dispose()
        provider.stop()
    }
}
