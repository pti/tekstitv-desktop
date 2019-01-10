package fi.reuna.tekstitv

import java.awt.Color
import java.awt.EventQueue
import javax.swing.JFrame

private var controller: Controller? = null

fun main(args: Array<String>) {
    Log.debug("begin")

//    System.setProperty("swing.aatext", "true")
//    System.setProperty("awt.useSystemAAFontSettings", "on")
//    System.setProperty("prism.lcdtext", "false")

    val frame = JFrame("TekstiTV")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.background = Color.BLACK
    frame.setBounds(220, 220, 500, 600) // TODO save and load size&pos from prefs
    frame.isVisible = true

    EventQueue.invokeLater {
        val panel = SubpagePanel()
        frame.contentPane.add(panel)
        controller = Controller(panel)
    }
}
