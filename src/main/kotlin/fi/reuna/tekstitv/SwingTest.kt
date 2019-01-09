package fi.reuna.tekstitv

import java.awt.*
import java.awt.geom.AffineTransform
import javax.swing.JFrame
import javax.swing.JPanel

private const val MAX_CHARS_PER_LINE = 43
private const val MAX_LINES_PER_PAGE = 20

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

    val font = Font(fontFamily, 0, 1)
    val widthFit = calculateFittingFontSize(font) { MAX_CHARS_PER_LINE * it.charWidth(0) <= width }
    val heightFit = calculateFittingFontSize(font) { MAX_LINES_PER_PAGE * it.height <= height }
    val size = Math.min(widthFit, heightFit)
    return font.deriveFont(size)
}

class PaintSpec(g: Graphics, val width: Int, val height: Int) {

    val font: Font
    val charWidth: Int
    val charHeight: Int
    val lineHeight: Int
    val fontMetrics: FontMetrics
    val background = Color.BLACK!!
    val foreground = Color.WHITE!!

    init {
        font = fontForSize(g, "Droid Sans Mono", width, height)
        fontMetrics = g.getFontMetrics(font)
        charWidth = fontMetrics.charWidth(0)
        charHeight = fontMetrics.height
        lineHeight = fontMetrics.height
    }
}

class PagePanelz : JPanel() {

    private var spec: PaintSpec? = null

    private fun checkSpec(): PaintSpec {

        if (spec == null || width != spec!!.width || height != spec!!.height) {
            spec = PaintSpec(graphics, width, height)
        }

        return spec!!
    }

    fun test(g: Graphics2D, pieces: Array<Piece>) {
        val spec = checkSpec()
        var x = 0
        var y = 0
        var row = 0
        g.font = spec.font

        g.color = Color.BLACK
        g.fillRect(0, 0, width, height)

        for (piece in pieces) {
            piece.paint(g, spec, x, y)

            if (piece.lineEnd) {
                y += spec.lineHeight
                row += 1
            }
        }
    }

    override fun paint(g: Graphics?) {
        super.paint(g)

        val g2d = g as Graphics2D
        val font = Font("Droid Sans Mono", 0, 17)
        val fm = g2d.getFontMetrics(font)

        g2d.color = Color(0, 60, 0)
        g2d.fillRect(0, 0, width, height)

        val sw = fm.charWidth(0).toDouble()
        val sh = fm.height.toDouble()

        val marginx = 10.0
        val marginy = 10.0
        val gap = 1.0
        var x = marginx.toInt()
        var y = marginy.toInt()

        val perRow = 10
        val num = 64
        val rows = Math.ceil(num.toDouble() / perRow).toInt()

        g2d.color = Color.WHITE

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
        g2d.font = font
        var text = "maksu voisi olla"

        for (i in 0 until rows) {
            val tx = x
            val ty = y + (sh + gap).toInt() * i

            text = "maksu voisi olla#$i"

            g2d.color = if (i % 2 == 0) Color.GREEN else Color.BLACK
            g2d.fillRect(tx, ty, fm.stringWidth(text), sh.toInt())

            g2d.color = Color.WHITE
            g2d.drawString(text, tx, ty + fm.ascent)
        }

        x += fm.stringWidth(text)

        g2d.color = Color.DARK_GRAY
        g2d.fillRect(x, y, (perRow * (sw + gap)).toInt(), (rows * (sh + gap)).toInt())

        for (i in 0 until num) {
            val r = i / perRow
            val c = i % perRow

            val at = AffineTransform()
            at.translate(x + c * gap, y + r * gap)
            at.scale(sw, sh)
            at.translate(c.toDouble(), r.toDouble())
            g2d.transform = at

            val v = 255
            g2d.color = Color(v, v, v)

            val symbol = BlockSymbol.get(i, GraphicsMode.CONNECTED)
            g2d.fill(symbol)
        }
    }
}

fun main(args: Array<String>) {
    Log.debug("begin")
    System.setProperty("prism.lcdtext", "false")
//    System.setProperty("swing.aatext", "true")
//    System.setProperty("awt.useSystemAAFontSettings", "on")
//    System.setProperty("swing.defaultlaf", "com.sun.java.swing.plaf.gtk.GTKLookAndFeel")

    val frame = JFrame("HelloWorldSwing")
    frame.defaultCloseOperation = JFrame.EXIT_ON_CLOSE
    frame.background = Color.BLACK
    frame.setBounds(20, 20, 800, 500)

    val t2 = PagePanelz()
    frame.contentPane.add(t2)
    frame.isVisible = true

}