package fi.reuna.tekstitv.ui

import javax.swing.JPanel
import kotlin.math.ceil

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
}
