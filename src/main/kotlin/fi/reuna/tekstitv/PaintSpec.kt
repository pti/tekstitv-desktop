package fi.reuna.tekstitv

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
    val background = Color.BLACK!!
    val foreground = Color.WHITE!!
    val contentWidth: Int
    val contentHeight: Int
    val margin = 10 // TODO from config
    val doubleHeightMultiplier = 2.0 // Defined in case one wants to disable use of double height lines at some point.

    init {
        font = fontForSize(g, "Fira Mono", width - 2 * margin, height - 2 * margin) // TODO from config
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
