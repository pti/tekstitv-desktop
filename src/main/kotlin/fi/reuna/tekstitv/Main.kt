package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import fi.reuna.tekstitv.ui.restoreWindowRectangle
import java.awt.EventQueue
import javax.swing.JFrame
import kotlin.concurrent.thread

fun main() {
    Log.debug("begin")

    // NavigationHistory can take a few tens of ms to load so do it while initializing the rest of the stuff.
    thread { NavigationHistory.instance }

    EventQueue.invokeLater {
        val cfg = Configuration.instance
        val frame = JFrame("Teksti-TV")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.background = cfg.backgroundColor
        frame.restoreWindowRectangle()

        val main = MainView()
        main.background = frame.background
        main.pagePanel.background = frame.background
        main.pageNumberView.background = frame.background
        main.shortcuts.background = frame.background

        frame.contentPane.add(main)
        frame.isVisible = true

        Controller(main, frame)
    }
}
