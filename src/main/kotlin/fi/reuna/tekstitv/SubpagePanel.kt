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

    var latestEvent: PageEvent? = null
        set(value) {
            field = value
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
        val ignoreLineAfterDoubleHeightOne = true

        g.font = spec.font
        var lastRowWasDoubleHeight = false

        for (piece in subpage.pieces) {

            // Sometimes double height rows are followed by lines with some content that isn't visible in the web version.
            // Perhaps there is some other way of not displaying that content, but for now just ignore the following row
            // (at least currently this results in correct looking pages).
            if (!lastRowWasDoubleHeight || !ignoreLineAfterDoubleHeightOne) {
                x += piece.paint(g, spec, x, y)
            }

            if (piece.lineEnd) {
                y += spec.lineHeight
                x = x0
                // If the last line was a double height one, then this line was ignored (if ignoreLineAfterDoubleHeightOne)
                // and nothing was drawn so effectively this line is never a double height one (even if it contained
                // double height content - sometimes the line following a dhei line can contain dhei tags too).
                lastRowWasDoubleHeight = piece.doubleHeight && (!lastRowWasDoubleHeight || !ignoreLineAfterDoubleHeightOne)
            }
        }
    }

    private fun paintErrorMessage(g: Graphics2D, spec: PaintSpec, event: PageEvent.Failed) {

        val errorMessage = when (event.type) {
            ErrorType.NOT_FOUND -> "Page ${event.location.page} not found"
            else -> "Error loading page ${event.location.page}"
        }

        val textWidth = SwingUtilities.computeStringWidth(spec.fontMetrics, errorMessage)
        val textHeight = spec.fontMetrics.height
        g.font = spec.font
        g.color = spec.foreground
        g.drawString(errorMessage, (width - textWidth) / 2, (height - textHeight) / 2)
    }

    override fun paintComponent(g: Graphics?) {
        Log.debug("")
        super.paintComponent(g)

        val g2d = g as Graphics2D
        val event = latestEvent
        val spec = checkSpec()

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        when (event) {
            is PageEvent.Loaded -> {
                paintSubpage(g2d, spec, event.subpage)
            }
            is PageEvent.Failed -> {
                paintErrorMessage(g2d, spec, event)
            }
        }
    }
}
