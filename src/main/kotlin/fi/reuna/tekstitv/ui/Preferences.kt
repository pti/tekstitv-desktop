package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.Controller
import java.util.prefs.Preferences
import javax.swing.JFrame

private const val PREF_WIN_X = "win_x"
private const val PREF_WIN_Y = "win_y"
private const val PREF_WIN_W = "win_w"
private const val PREF_WIN_H = "win_h"

fun getAppPreferences(): Preferences = Preferences.userNodeForPackage(Controller::class.java)

fun JFrame.saveWindowRectangle(prefs: Preferences) {
    prefs.putInt(PREF_WIN_X, x)
    prefs.putInt(PREF_WIN_Y, y)
    prefs.putInt(PREF_WIN_W, width)
    prefs.putInt(PREF_WIN_H, height)
    prefs.flush()
}

fun JFrame.restoreWindowRectangle(prefs: Preferences) {

    if (prefs.get(PREF_WIN_X, null) != null && prefs.get(PREF_WIN_W, null) != null) {
        setLocation(prefs.getInt(PREF_WIN_X, 0), prefs.getInt(PREF_WIN_Y, 0))
    }

    setSize(prefs.getInt(PREF_WIN_W, 500), prefs.getInt(PREF_WIN_H, 600))
}
