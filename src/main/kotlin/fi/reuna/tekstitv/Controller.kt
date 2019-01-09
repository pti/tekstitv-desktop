package fi.reuna.tekstitv

import com.github.thomasnield.rxkotlinfx.events
import com.github.thomasnield.rxkotlinfx.observeOnFx
import javafx.application.Platform
import javafx.scene.input.KeyCode
import javafx.scene.input.KeyEvent
import javafx.scene.text.Text
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

class Controller(private val text: Text) {

    private val digitBuffer = DigitBuffer()

    // TODO ^ stop autorefresh when minimized - kts JXTest

    init {
        val replacer = Regex("\\[\\w{4}\\]")
        val provider = PageProvider()

        provider.observe()
                .observeOnFx()
                .subscribe {

                    when (it) {
                        is PageEvent.Loaded -> {
                            Log.debug("got ${it.page.location}")
                            text.text = it.page.content.replace(replacer, " ")
                        }
                        is PageEvent.Failed -> {
                            Log.error("failed to load page ${it.location}: ${it.error}")
                            text.text = "failed to load page ${it.location}: ${it.error}"
                        }
                        is PageEvent.NotFound -> {
                            Log.error("page not found: ${it.location}")
                            text.text = "page not found: ${it.location}"
                        }
                    }
                }

        provider.observe()
                .debounce(1, TimeUnit.MINUTES)
                .subscribe { provider.reload() }

        Log.debug("provider observer set up")
        provider.set(100) // TODO initial page defined in config

        text.scene.events(KeyEvent.KEY_PRESSED)
                .observeOnFx()
                .subscribe { e ->

                    if (e.isControlDown) {

                        when (e.code) {
                            KeyCode.R -> provider.reload()
                            KeyCode.Q -> Platform.exit()
                            else -> return@subscribe
                        }

                    } else {

                        when (e.code) {
                            KeyCode.LEFT -> provider.prevSubpage()
                            KeyCode.RIGHT -> provider.nextSubpage()
                            KeyCode.UP -> provider.nextPage()
                            KeyCode.DOWN -> provider.prevPage()
                            KeyCode.BACK_SPACE -> provider.back()
                            KeyCode.F5 -> provider.reload()
                            else -> return@subscribe
                        }
                    }

                    digitBuffer.inputEnded()
                }

        // TODO display some kind of loading indicator if request takes a long time
        // TODO display and update page number when consuming key events -> observable for digitbuffer / provide property

        text.scene.events(KeyEvent.KEY_TYPED)
                .observeOnFx()
                .subscribe {
                    val char = it.character.first()

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
    }
}
