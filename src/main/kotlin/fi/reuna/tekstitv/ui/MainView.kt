package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ConfigurationProvider
import fi.reuna.tekstitv.Controller
import fi.reuna.tekstitv.Log
import java.awt.event.WindowEvent
import java.util.prefs.Preferences
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.ceil

private const val PREF_WIN_X = "win_x"
private const val PREF_WIN_Y = "win_y"
private const val PREF_WIN_W = "win_w"
private const val PREF_WIN_H = "win_h"

class MainView: JPanel() {

    val pagePanel = SubpagePanel()
    val pageNumberView = PageNumberView()
    val shortcuts = ShortcutsBar()

    init {
        layout = null
        add(pagePanel)
        add(shortcuts)
        add(pageNumberView)
    }

    override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)

        val topBarH = ceil(height * 0.04).toInt()
        val bottomBarH = ceil(height * 0.10).toInt()
        val contentY = y + topBarH
        val contentH = height - topBarH - bottomBarH
        val bottomBarY = contentY + contentH

        pageNumberView.setBounds(x, y, width, topBarH)
        pagePanel.setBounds(x, contentY, width, contentH)
        shortcuts.setBounds(x, bottomBarY, width, bottomBarH)
    }

    fun createUI() {
        val cfg = ConfigurationProvider.cfg
        val prefs = Preferences.userNodeForPackage(Controller::class.java)
        val frame = JFrame("Teksti-TV")
        frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
        frame.background = cfg.backgroundColor

        if (prefs.get(PREF_WIN_X, null) != null && prefs.get(PREF_WIN_W, null) != null) {
            frame.setLocation(prefs.getInt(PREF_WIN_X, 0), prefs.getInt(PREF_WIN_Y, 0))
        }

        frame.setSize(prefs.getInt(PREF_WIN_W, 500), prefs.getInt(PREF_WIN_H, 600))

        background = frame.background
        pagePanel.background = frame.background
        pageNumberView.background = frame.background
        shortcuts.background = frame.background

        frame.contentPane.add(this)
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
        Controller(this, frame)
        Log.debug("done creating controller")
    }
}
