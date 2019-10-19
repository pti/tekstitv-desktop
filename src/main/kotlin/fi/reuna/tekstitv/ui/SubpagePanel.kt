package fi.reuna.tekstitv.ui

import fi.reuna.tekstitv.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.SwingUtilities
import kotlin.math.ceil

interface PageLinkListener {
    fun onPageLinkClicked(link: PageLink)
}

class SubpagePanel : JPanel() {

    private var spec: PaintSpec? = null
    private var currentSubpage: Subpage? = null
    private var drawRect = Rectangle()
    private var focusedLink: PageLink? = null
    private var focusedLinkRect: Rectangle? = null // Link rectangle in pixel coordinates.
    private val mouseAdapter = MouseHandler()

    var pageLinkListener: PageLinkListener? = null

    init {
        if (Configuration.instance.mouseEnabled) {
            addMouseMotionListener(mouseAdapter)
            addMouseListener(mouseAdapter)
        }
    }

    fun stop() {
        pageLinkListener = null
        removeMouseMotionListener(mouseAdapter)
    }

    var latestEvent: PageEvent? = null
        set(value) {
            focusedLinkRect = null
            focusedLink = null
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
        drawRect.setBounds(x0, y, spec.contentWidth, spec.contentHeight)

        g.font = spec.font

        for (piece in subpage.pieces) {
            x += piece.paint(g, spec, x, y)

            if (piece.lineEnd) {
                y += ceil(spec.lineHeight * piece.heightMultiplier(spec)).toInt()
                x = x0
            }
        }

        if (focusedLinkRect != null) {
            g.color = Configuration.instance.linkFocusColor
            g.fill(focusedLinkRect)
        }
    }

    private fun paintMessage(g: Graphics2D, spec: PaintSpec, message: String, width: Int, height: Int) {
        val textWidth = SwingUtilities.computeStringWidth(spec.fontMetrics, message)
        val textHeight = spec.fontMetrics.height
        g.font = spec.font
        g.color = spec.foreground
        g.drawString(message, (width - textWidth) / 2, (height - textHeight) / 2)
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)

        val event = latestEvent
        val spec = checkSpec(width, height)

        val g2d = g as Graphics2D
        g2d.applyDesktopHints()
        // In some cases (specific font family, font size and prolly hinting/anti-aliasing setting combo) computing
        // the character width (also SwingUtilities.computeStringWidth) results in a incorrect value.
        // This results in text overflowing outside the intended page area.
        // Not calling applyDesktopHints() can fix the width results but rendering quality is likely to be subpar.

        when (event) {
            is PageEvent.Loaded -> {
                currentSubpage = event.subpage
                paintSubpage(g2d, spec, event.subpage, width, height)
            }
            is PageEvent.Failed -> {
                paintMessage(g2d, spec, event.getErrorMessage(), width, height)
            }
            is PageEvent.Ignored -> {
                currentSubpage?.let { paintSubpage(g2d, spec, it, width, height) }
            }
            is PageEvent.Loading -> {
                paintMessage(g2d, spec, "Loading page ${event.req.location.page}", width, height)
            }
        }
    }

    private fun PageEvent.Failed.getErrorMessage(): String {
        val status = (error as? HttpException)?.status ?: -1

        when (req.direction) {
            Direction.NEXT -> return "Error loading next page ($status)"
            Direction.PREV -> return "Error loading previous page ($status)"
        }

        return when (type) {
            ErrorType.NOT_FOUND -> "Page ${req.location.page} not found"
            else -> "Error loading page ${req.location.page} ($status)"
        }
    }

    inner class MouseHandler: MouseAdapter() {

        override fun mouseMoved(e: MouseEvent?) {
            val sub = currentSubpage
            val spec = spec

            if (e == null || sub == null || spec == null) return

            val cx = (e.x - drawRect.x) / spec.charWidth
            val cy = (e.y - drawRect.y) / spec.lineHeight

            if (focusedLinkRect?.contains(cx, cy) == true) {
                return
            }

            val link = sub.findLink(cx, cy)

            if (link?.rect != focusedLinkRect) {
                focusedLink = link
                focusedLinkRect = link?.rect?.let {
                    Rectangle(drawRect.x + it.x * spec.charWidth,
                            drawRect.y + it.y * spec.charHeight,
                            it.width * spec.charWidth,
                            it.height * spec.charHeight)
                }

                repaint()
            }
        }

        override fun mouseExited(e: MouseEvent?) {

            if (focusedLinkRect != null) {
                focusedLinkRect = null
                focusedLink = null
                repaint()
            }
        }

        override fun mouseClicked(e: MouseEvent?) {
            focusedLink?.let { pageLinkListener?.onPageLinkClicked(it) }
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
