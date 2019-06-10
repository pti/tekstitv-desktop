package fi.reuna.tekstitv

import java.awt.Color
import java.awt.EventQueue
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.JFrame

private const val PREF_WIN_X = "win_x"
private const val PREF_WIN_Y = "win_y"
private const val PREF_WIN_W = "win_w"
private const val PREF_WIN_H = "win_h"

class Main {

    fun createUI() {
        val prefs = Preferences.userNodeForPackage(Controller::class.java)
        val frame = JFrame("Teksti-TV")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.background = Color.BLACK

        if (prefs.get(PREF_WIN_X, null) != null && prefs.get(PREF_WIN_W, null) != null) {
            frame.setLocation(prefs.getInt(PREF_WIN_X, 0), prefs.getInt(PREF_WIN_Y, 0))
        }

        frame.setSize(prefs.getInt(PREF_WIN_W, 500), prefs.getInt(PREF_WIN_H, 600))

        val panel = SubpagePanel()
        frame.contentPane.add(panel)
        frame.isVisible = true
        Log.debug("done")

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
    EventQueue.invokeLater { Main().createUI() }
}
