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

    // Preferences by default syncs every 30 seconds (at least on oracle 1.8.0_181).
    // In this case it is completely unnecessary => increase the interval to a week.
    // https://stackoverflow.com/questions/17376200/java-util-preferences-constantly-accesses-disk-about-every-30-secs
    System.setProperty("java.util.prefs.syncInterval", "604800")

    EventQueue.invokeLater {
        val cfg = ConfigurationProvider.cfg
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
