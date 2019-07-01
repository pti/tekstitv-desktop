package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.Configuration
import fi.reuna.tekstitv.ErrorType
import fi.reuna.tekstitv.PageEvent
import fi.reuna.tekstitv.Subpage
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.Toolkit
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil

class SubpagePanel : JPanel() {

    private var spec: PaintSpec? = null

    var latestEvent: PageEvent? = null
        set(value) {
            field = value
            repaint()
        }

    private fun checkSpec(width: Int, height: Int): PaintSpec {

        if (spec == null || width != spec!!.width || height != spec!!.height) {
            spec = PaintSpec(graphics, width, height)
        }

        return spec!!
    }

    private fun paintSubpage(g: Graphics2D, spec: PaintSpec, subpage: Subpage, width: Int, height: Int) {
        // Most of the pages have an empty first column, and in those cases the content looks better when
        // x0 is shifted a bit to the left (center align the assumed actual content).
        val xAdjust = if (Configuration.instance.shiftEmptyFirstColumn && subpage.emptyFirstColumn) spec.charWidth else 0
        val x0 = (width - spec.contentWidth - xAdjust) / 2
        var x = x0
        var y = (height - spec.contentHeight) / 2

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

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val event = latestEvent
        val spec = checkSpec(width, height)

        val g2d = g as Graphics2D
        g2d.applyDesktopHints()

        when (event) {
            is PageEvent.Loaded -> {
                paintSubpage(g2d, spec, event.subpage, width, height)
            }
            is PageEvent.Failed -> {
                paintErrorMessage(g2d, spec, event, width, height)
            }
        }
    }
}

fun Graphics2D.applyDesktopHints() {
    // One can override the default hints by specifying the system property awt.useSystemAAFontSettings,
    // e.g. java -Dawt.useSystemAAFontSettings=on -jar tekstitv-desktop.jar.
    // All possible property values are defined here: https://docs.oracle.com/javase/8/docs/technotes/guides/2d/flags.html
    val tk = Toolkit.getDefaultToolkit()
    val dh = tk.getDesktopProperty("awt.font.desktophints") as? Map<*, *>
    if (dh != null) addRenderingHints(dh)
}
