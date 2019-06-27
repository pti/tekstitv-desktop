package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ConfigurationProvider
import fi.reuna.tekstitv.NavigationHistory
import fi.reuna.tekstitv.PageEvent
import java.awt.*
import javax.swing.JPanel
import kotlin.math.ceil

class ShortcutsBar: JPanel() {

    private val shortcutColors = arrayOf(Color.RED.darker(), Color.GREEN.darker(), Color.YELLOW.darker(), Color.BLUE.brighter())
    private val shortcutChars = charArrayOf('r', 'g', 'y', 'b')

    private var shortcuts: Array<Int> = emptyArray()

    fun update(event: PageEvent?) {

        if (event is PageEvent.Loaded) {
            val number = event.subpage.location.page
            val max = shortcutColors.size
            shortcuts = NavigationHistory.instance.topHits(number, max).toTypedArray()
            val remaining = max - shortcuts.size

            if (remaining > 0) {
                val links = event.subpage.links
                        .filter { !shortcuts.contains(it) }
                        .take(remaining)

                if (links.isNotEmpty()) {
                    val tmp = linkedSetOf<Int>()
                    tmp.addAll(shortcuts)
                    tmp.addAll(links)
                    shortcuts = tmp.toTypedArray()
                }
            }

        } else {
            shortcuts = emptyArray()
        }

        repaint()
    }

    fun getShortcut(colorCharacter: Char): Int? {
        val index = shortcutChars.indexOf(colorCharacter)

        return if (index > -1 && index < shortcuts.size) {
            shortcuts[index]
        } else {
            null
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val g2d = g as Graphics2D
        g2d.applyDesktopHints()

        val cfg = ConfigurationProvider.cfg
        val fontSize = ceil(height * cfg.shortcutFontSizer).toInt()
        g.font = Font(cfg.shortcutFontFamily, Font.PLAIN, fontSize)
        val fm = g.getFontMetrics(g.font)

        val numShortcuts = shortcutColors.size
        val approxMaxTextWidth = fm.stringWidth("000")
        val shortcutContentW = (approxMaxTextWidth * 1.8).toInt()
        val colorW = 3
        val shortcutH = (fm.height * 1.32).toInt()
        val spacing = (approxMaxTextWidth * 0.6).toInt()
        val totalW = numShortcuts * (shortcutContentW + colorW) + (numShortcuts - 1) * spacing

        val bg = cfg.shortcutBackground
        val fg = cfg.shortcutForeground
        var x = (width - totalW) / 2
        val y = (height - shortcutH) / 2
        val textY = y + (shortcutH - fm.height) / 2 + fm.ascent

        for ((index, value) in shortcuts.withIndex()) {
            g.color = shortcutColors[index]
            g.fillRect(x, y, colorW, shortcutH)
            x += colorW

            g.color = bg
            g.fillRect(x, y, shortcutContentW, shortcutH)

            g.color = fg
            g.drawString(value.toString(), x + (shortcutContentW - approxMaxTextWidth) / 2, textY)

            x += shortcutContentW + spacing
        }
    }
}
