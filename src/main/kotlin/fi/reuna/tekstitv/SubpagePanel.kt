package fi.reuna.tekstitv

import java.awt.Color
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.SwingUtilities


class SubpagePanel : JPanel() {

    private var spec: PaintSpec? = null

    init {
        background = Color.BLACK
    }

    var subpage: Subpage? = null
        set(value) {
            field = value

            if (value != null) {
                errorMessage = null
            }

            repaint()
        }

    var errorMessage: String? = null
        set(value) {
            field = value

            if (value != null) {
                subpage = null
            }

            repaint()
        }

    private fun checkSpec(): PaintSpec {

        if (spec == null || width != spec!!.width || height != spec!!.height) {
            spec = PaintSpec(graphics, width, height)
        }

        return spec!!
    }

    private fun paintSubpage(g: Graphics2D, spec: PaintSpec, subpage: Subpage) {
        val x0 = (width - 2 * spec.margin - spec.contentWidth) / 2 + spec.margin
        var x = x0
        var y = (height - 2 * spec.margin - spec.contentHeight) / 2 + spec.margin

        g.font = spec.font
        var lastRowWasDoubleHeight = false

        for (piece in subpage.pieces) {

            // Sometimes double height rows are followed by lines with some content that isn't visible in the web version.
            // Perhaps there is some other way of not displaying that content, but for now just ignore the following row
            // (at least currently this results in correct looking pages).
            if (!lastRowWasDoubleHeight) {
                x += piece.paint(g, spec, x, y)
            }

            if (piece.lineEnd) {
                y += spec.lineHeight
                x = x0
                lastRowWasDoubleHeight = piece.doubleHeight
            }
        }
    }

    private fun paintErrorMessage(g: Graphics2D, spec: PaintSpec, errorMessage: String) {
        val textWidth = SwingUtilities.computeStringWidth(spec.fontMetrics, errorMessage)
        val textHeight = spec.fontMetrics.height
        g.color = spec.foreground
        g.drawString(errorMessage, (width - textWidth) / 2, (height - textHeight) / 2)
    }

    override fun paint(g: Graphics?) {
        super.paint(g)
        val g2d = g as Graphics2D
        val subpage = subpage
        val errorMessage = errorMessage
        val spec = checkSpec()

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        if (errorMessage != null) {
            paintErrorMessage(g2d, spec, errorMessage)
        }

        if (subpage != null) {
            paintSubpage(g2d, spec, subpage)
        }
    }
}
