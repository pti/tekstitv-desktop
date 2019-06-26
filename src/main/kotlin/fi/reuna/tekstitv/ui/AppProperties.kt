package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.Log
import fi.reuna.tekstitv.getIntProperty
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Paths
import java.util.*
import javax.swing.JFrame

private const val PREF_WIN_X = "win_x"
private const val PREF_WIN_Y = "win_y"
private const val PREF_WIN_W = "win_w"
private const val PREF_WIN_H = "win_h"

private fun getAppPreferencesFile(): File {
    return (System.getProperty("tekstitv.prefs")?.let { Paths.get(it) }
            ?: Paths.get(System.getProperty("user.home"), ".tekstitv", "app.properties"))
            .toFile()
}

fun JFrame.saveWindowRectangle() {
    val p = Properties()
    p.setProperty(PREF_WIN_X, x.toString())
    p.setProperty(PREF_WIN_Y, y.toString())
    p.setProperty(PREF_WIN_W, width.toString())
    p.setProperty(PREF_WIN_H, height.toString())
    getAppPreferencesFile().bufferedWriter().use { p.store(it, null) }
}

fun JFrame.restoreWindowRectangle() {
    val p = Properties()

    try {
        getAppPreferencesFile().bufferedReader().use { p.load(it) }

    } catch (fnfe: FileNotFoundException) {
        Log.info("app.properties not found - using defaults")

    } catch (e: Exception) {
        Log.error("failed to load app.properties file", e)
    }

    val x = p.getIntProperty(PREF_WIN_X, null)
    val y = p.getIntProperty(PREF_WIN_Y, null)

    if (x != null && y != null) {
        setLocation(x, y)
    }

    setSize(p.getIntProperty(PREF_WIN_W, 430)!!, p.getIntProperty(PREF_WIN_H, 600)!!)
}
