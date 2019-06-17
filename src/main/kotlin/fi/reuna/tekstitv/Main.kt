package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.MainView
import java.awt.EventQueue
import kotlin.concurrent.thread

fun main() {
    Log.debug("begin")

    // TTVService instance initialization, or at least building the OkHttpClient, takes quite a while so
    // do it as soon as possible. This way it might be ready already once the UI has been created.
    // If the beginning of time is when Log was initialized and t1 is the time at which the first page request
    // is done, then in one setup t1 decreased from ~500ms to ~360ms by initializing TTVService in a separate thread.
    thread {
        Log.debug("start creating TTVService instance")
        TTVService.instance
        Log.debug("done creating TTVService instance")
    }

    // Preferences by default syncs every 30 seconds (at least on oracle 1.8.0_181).
    // In this case it is completely unnecessary => increase the interval to a week.
    // https://stackoverflow.com/questions/17376200/java-util-preferences-constantly-accesses-disk-about-every-30-secs
    System.setProperty("java.util.prefs.syncInterval", "604800")

    EventQueue.invokeLater { MainView().createUI() }
}
