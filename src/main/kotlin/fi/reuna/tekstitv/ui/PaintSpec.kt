package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ConfigurationProvider
import java.awt.Color
import java.awt.Font
import java.awt.FontMetrics
import java.awt.Graphics

class PaintSpec(g: Graphics, val width: Int, val height: Int) {

    val font: Font
    val charWidth: Int
    val charHeight: Int
    val lineHeight: Int
    val fontMetrics: FontMetrics
    val foreground = Color.WHITE!!
    val contentWidth: Int
    val contentHeight: Int
    val margin: Int

    init {
        val cfg = ConfigurationProvider.cfg
        margin = cfg.margin
        font = fontForSize(g, cfg.fontFamily, width - 2 * margin, height - 2 * margin)
        fontMetrics = g.getFontMetrics(font)
        charWidth = fontMetrics.charWidth('0')
        charHeight = fontMetrics.height
        lineHeight = fontMetrics.height
        contentWidth = MAX_CHARS_PER_LINE * charWidth
        contentHeight = MAX_LINES_PER_PAGE * lineHeight
    }
}

private const val MAX_CHARS_PER_LINE = 40
private const val MAX_LINES_PER_PAGE = 23

private fun fontForSize(g: Graphics, fontFamily: String, width: Int, height: Int): Font
{
    fun calculateFittingFontSize(font: Font, inspector: (fm: FontMetrics) -> Boolean): Float {
        var size = 1.0f

        while (true) {

            if (!inspector(g.getFontMetrics(font.deriveFont(size)))) {
                return size - 1.0f
            } else {
                size += 1.0f
            }
        }
    }

    val font = Font(fontFamily, Font.PLAIN, 1)
    val widthFit = calculateFittingFontSize(font) { MAX_CHARS_PER_LINE * it.charWidth('0') <= width }
    val heightFit = calculateFittingFontSize(font) { MAX_LINES_PER_PAGE * it.height <= height }
    val size = Math.min(widthFit, heightFit)
    return font.deriveFont(size)
}
