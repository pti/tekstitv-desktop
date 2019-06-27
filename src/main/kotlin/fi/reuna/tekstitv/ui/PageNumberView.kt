package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ConfigurationProvider
import fi.reuna.tekstitv.DigitBufferListener
import java.awt.*
import javax.swing.JPanel
import kotlin.math.ceil

class PageNumberView: JPanel(), DigitBufferListener {

    private var content: String? = null
    private var inputActive = false

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val g2d = g as Graphics2D
        g2d.applyDesktopHints()

        if (content != null) {
            val cfg = ConfigurationProvider.cfg
            val fontSize = ceil(height * cfg.pageNumberFontSizer).toInt()
            g.font = Font(cfg.pageNumberFontFamily, Font.PLAIN, fontSize)
            val fm = g.getFontMetrics(g.font)

            g.color = if (inputActive) cfg.pageNumberColorActive else cfg.pageNumberColorInactive
            g.drawString(content, (width - fm.stringWidth(content)) / 2, (height - fm.height) / 2 + fm.ascent)
        }
    }

    override fun onDigitBufferChanged(content: String, inputActive: Boolean) {
        this.content = content
        this.inputActive = inputActive
        repaint()
    }
}