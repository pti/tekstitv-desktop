package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ConfigurationProvider
import fi.reuna.tekstitv.NavigationHistory
import fi.reuna.tekstitv.PageEvent
import java.awt.Color
import java.awt.Font
import java.awt.Graphics2D
import kotlin.math.ceil

class ShortcutsBar {

    private val shortcutChars = listOf('r', 'g', 'y', 'b')
    private val shortcutColors = listOf(Color.RED.darker(), Color.GREEN.darker(), Color.YELLOW.darker(), Color.BLUE.brighter())

    private var shortcuts: List<Int> = emptyList()

    fun update(event: PageEvent?) {

        if (event is PageEvent.Loaded) {
            val number = event.subpage.location.page
            val max = shortcutColors.size
            shortcuts = NavigationHistory.instance.topHits(number, max)
            val remaining = max - shortcuts.size

            if (remaining > 0) {
                shortcuts = shortcuts.toMutableList().apply {
                    addAll(event.subpage.links.take(remaining))
                }
            }

        } else {
            shortcuts = emptyList()
        }
    }

    fun getShortcut(colorCharacter: Char): Int? {
        val index = shortcutChars.indexOf(colorCharacter)

        return if (index > -1 && index < shortcuts.size) {
            shortcuts[index]
        } else {
            null
        }
    }

    fun paint(g: Graphics2D, width: Int, height: Int) {
        val cfg = ConfigurationProvider.cfg
        val fontSize = ceil(height * cfg.shortcutFontSizer).toInt()
        g.font = Font(cfg.shortcutFontFamily, Font.PLAIN, fontSize)
        val fm = g.getFontMetrics(g.font)

        val numShortcuts = shortcutColors.size
        val approxMaxTextWidth = fm.stringWidth("000")
        val shortcutContentW = (approxMaxTextWidth * 1.8).toInt()
        val colorW = 3
        val shortcutH = (fm.height * 1.4).toInt()
        val spacing = (approxMaxTextWidth * 0.6).toInt()
        val totalW = numShortcuts * (shortcutContentW + colorW) + (numShortcuts - 1) * spacing

        val bg = Color(cfg.shortcutBackground)
        val fg = Color(cfg.shortcutForeground)
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
