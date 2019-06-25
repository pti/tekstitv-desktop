package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import java.awt.EventQueue
import kotlin.concurrent.thread

fun main() {
    Log.debug("begin")

    // By creating a TTVService instance (loading associated classes) while initializing the UI, the first request
    // is made a few 10ms faster (~240ms instead of 270ms).
    thread {
        Log.debug("start creating TTVService instance")
        TTVService()
        Log.debug("done creating TTVService instance")
    }

    // Preferences by default syncs every 30 seconds (at least on oracle 1.8.0_181).
    // In this case it is completely unnecessary => increase the interval to a week.
    // https://stackoverflow.com/questions/17376200/java-util-preferences-constantly-accesses-disk-about-every-30-secs
    System.setProperty("java.util.prefs.syncInterval", "604800")

    EventQueue.invokeLater { MainView().createUI() }
}
