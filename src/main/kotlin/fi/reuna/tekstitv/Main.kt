package fi.reuna.tekstitv

import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.JFrame
import kotlin.concurrent.thread

private const val PREF_WIN_X = "win_x"
private const val PREF_WIN_Y = "win_y"
private const val PREF_WIN_W = "win_w"
private const val PREF_WIN_H = "win_h"

class Main {

    fun createUI() {
        val prefs = Preferences.userNodeForPackage(Controller::class.java)
        val frame = JFrame("Teksti-TV")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.background = ConfigurationProvider.cfg.backgroundColor

        if (prefs.get(PREF_WIN_X, null) != null && prefs.get(PREF_WIN_W, null) != null) {
            frame.setLocation(prefs.getInt(PREF_WIN_X, 0), prefs.getInt(PREF_WIN_Y, 0))
        }

        frame.setSize(prefs.getInt(PREF_WIN_W, 500), prefs.getInt(PREF_WIN_H, 600))

        val panel = SubpagePanel()
        panel.background = frame.background
        frame.contentPane.add(panel)
        frame.isVisible = true

        frame.observeWindowEvents()
                .filter { it.id == WindowEvent.WINDOW_CLOSING }
                .subscribe {
                    prefs.putInt(PREF_WIN_X, frame.x)
                    prefs.putInt(PREF_WIN_Y, frame.y)
                    prefs.putInt(PREF_WIN_W, frame.width)
                    prefs.putInt(PREF_WIN_H, frame.height)
                    prefs.flush()
                }

        Log.debug("begin create controller")
        Controller(panel, frame)
        Log.debug("done creating controller")
    }
}

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

    EventQueue.invokeLater { Main().createUI() }
}
