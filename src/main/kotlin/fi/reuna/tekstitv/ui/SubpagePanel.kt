package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.ErrorType
import fi.reuna.tekstitv.Log
import fi.reuna.tekstitv.PageEvent
import fi.reuna.tekstitv.Subpage
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil

class SubpagePanel : JPanel() {

    private var spec: PaintSpec? = null
    val shortcuts = ShortcutsBar()

    var latestEvent: PageEvent? = null
        set(value) {
            field = value

            if (value is PageEvent.Loaded) {
                shortcuts.currentPage = value.subpage.location.page
            } else {
                shortcuts.currentPage = null
            }

            repaint()
        }

    private fun checkSpec(width: Int, height: Int): PaintSpec {

        if (spec == null || width != spec!!.width || height != spec!!.height) {
            spec = PaintSpec(graphics, width, height)
        }

        return spec!!
    }

    private fun paintSubpage(g: Graphics2D, spec: PaintSpec, subpage: Subpage, width: Int, height: Int) {
        val x0 = (width - 2 * spec.margin - spec.contentWidth) / 2 + spec.margin
        var x = x0
        var y = (height - 2 * spec.margin - spec.contentHeight) / 2 + spec.margin

        g.font = spec.font

        for (piece in subpage.pieces) {
            x += piece.paint(g, spec, x, y)

            if (piece.lineEnd) {
                y += ceil(spec.lineHeight * piece.heightMultiplier(spec)).toInt()
                x = x0
            }
        }
    }

    private fun paintErrorMessage(g: Graphics2D, spec: PaintSpec, event: PageEvent.Failed, width: Int, height: Int) {

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
        val contentH = ceil(height * 0.92).toInt()
        val shortcutsH = height - contentH
        val spec = checkSpec(width, contentH)

        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

        when (event) {
            is PageEvent.Loaded -> {
                paintSubpage(g2d, spec, event.subpage, width, contentH)
            }
            is PageEvent.Failed -> {
                paintErrorMessage(g2d, spec, event, width, contentH)
            }
        }

        g2d.translate(0, contentH)
        shortcuts.paint(g2d, width, shortcutsH)
    }
}
