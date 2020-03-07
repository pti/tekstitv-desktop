package fi.reuna.tekstitv

import fi.reuna.tekstitv.ui.PaintSpec
import java.awt.Color
import java.awt.Graphics2D
import java.util.*
import java.util.regex.Pattern
import kotlin.math.ceil

/** Character code point offset for block symbols. */
const val BLOCK_SYMBOL_OFFSET_START = 0xE200
const val BLOCK_SYMBOL_OFFSET_END = 0xE27F

val BLOCK_SYMBOL_RANGE = BLOCK_SYMBOL_OFFSET_START..BLOCK_SYMBOL_OFFSET_END

enum class GraphicsMode(val range: IntRange) {
    CONTIGUOUS(0xE200..0xE23F),
    SEPARATED(0xE240..0xE27F)
}

class PagePiece(var foreground: Color,
                val background: Color?,
                var mode: GraphicsMode?,
                var content: String,
                var lineStart: Boolean = false,
                var lineEnd: Boolean = false,
                var doubleHeight: Boolean = false)
{
    fun heightMultiplier(spec: PaintSpec): Double {
        return if (doubleHeight) spec.doubleHeightMultiplier else 1.0
    }

    fun paint(g: Graphics2D, spec: PaintSpec, x: Int, y: Int): Int {

        if (content.isEmpty()) {
            return 0
        }

        val multiplier = heightMultiplier(spec)
        val contentWidth = spec.charWidth * content.length

        if (background != null) {
            g.color = background
            g.fillRect(x, y, contentWidth, ceil(spec.charHeight * multiplier).toInt())
        }

        g.color = foreground
        g.font = spec.font
        val restoreTransform = g.transform
        var tx = x.toDouble()
        val ty = y.toDouble()

        fun drawTextPiece(str: String) {
            g.translate(tx, ty + spec.fontMetrics.ascent * multiplier)
            g.scale(1.0, 1.0 * multiplier)
            g.drawString(str, 0, 0)
            g.transform = restoreTransform
        }

        if (mode != null) {

            for (code in content.chars()) {

                if (code in BLOCK_SYMBOL_RANGE) {
                    val symbolCode = code - BLOCK_SYMBOL_OFFSET_START
                    val symbol = BlockSymbol.get(symbolCode)

                    // Block symbol shapes are within coordinates (0,0) (1,1) so scale to match the font size.
                    g.transform = restoreTransform
                    g.translate(tx, ty)
                    g.scale(spec.charWidth.toDouble(), spec.charHeight.toDouble() * multiplier)

                    g.fill(symbol)

                    g.transform = restoreTransform

                } else {
                    drawTextPiece(code.toChar().toString())
                }

                tx += spec.charWidth
            }

        } else {
            drawTextPiece(content)
        }

        return contentWidth
    }
}

/**
 * Converts teletext data received from the server to objects of type [PagePiece] that are then easier to handle while painting.
 *
 * The content consists of spacing attribute tags (3-4 characters inside square brackets) and characters.
 * Depending on the mode defined by a spacing attribute, characters can point to graphics mode symbols (block symbols)
 * or to normal text characters.
 *
 * The original content is split to lines (separated by newline-char). The result specifies line changes in [PagePiece.lineEnd].
 *
 * Note that graphics mode symbols are not glyphs in a font but instead created at runtime (see [BlockSymbol]).
 * This enables using whatever monospaced font for rendering the actual text content and exact control over the
 * painting of graphics mode symbols. If the symbols were defined in the font it is possible that in certain font sizes
 * there would be gaps between lines or adjacent symbols (at least with JavaFX/Swing/AWT font renderers).
 * Anti-aliasing could also worsen the graphics quality. There are some fonts that already include the necessary
 * graphics mode symbols, but the readability of the text isn't necessarily as good as with some more common monospaced fonts.
 *
 * Graphics mode symbols in the content received from the server are all in ASCII-range. There are three categories of
 * symbols: connected, separated and characters. First two are block symbols and the last is a set of symbols that map
 * standard to ASCII characters. Connected and separated mode symbols are offset by 0xe200 so that one can select the
 * correct set of symbols to draw. The 'characters' category characters are just mapped to equivalent ASCII characters
 * so they can be drawn as normal text.
 *
 * Double height mode isn't supported.
 */
fun pageContentToPieces(lines: List<String>): List<PagePiece> {
    // Sometimes double height rows are followed by lines with some content that isn't visible in the web version.
    // Perhaps there is some other way of not displaying that content, but for now just ignore the following row
    // (at least currently this results in correct looking pages). These lines can even contain dhei tags.
    val ignoreLineAfterDoubleHeightOne = true

    val ret = ArrayList<PagePiece>()
    var lastRowWasDoubleHeight = false

    for (line in lines) {

        if (ignoreLineAfterDoubleHeightOne && lastRowWasDoubleHeight) {
            lastRowWasDoubleHeight = false
        } else {
            val pieces = lineToPieces(line)
            ret += pieces
            lastRowWasDoubleHeight = pieces.lastOrNull()?.doubleHeight ?: false
        }
    }

    return ret
}

private val tagPattern = Pattern.compile("""\{[A-Z]{1,3}[a-z]*}""")
private val capturingTagPattern = Pattern.compile("""^\{([A-Z]{1,3}[a-z]*)}$""")

private fun lineToPieces(line: String): List<PagePiece> {
    val defaultFg: Color = Color.WHITE

    if (line.trim().isEmpty()) {
        return listOf(PagePiece(defaultFg, background = null, content = "", lineStart = true, lineEnd = true, mode = null))
    }

    val strings = splitByTags(line)
    val pieces = ArrayList<PagePiece>(strings.size)

    var bg: Color? = null
    var fg: Color = defaultFg
    var graphicsMode: GraphicsMode? = null
    val content = StringBuilder()
    var doubleHeight = false
    val defaultSpaceChar = ' '
    var heldGraphicsChar = defaultSpaceChar
    var hold = false

    fun pieceBreak() {

        if (content.isEmpty()) {
            return
        }

        val last = pieces.lastOrNull()
        var startNewPiece = (last == null)

        if (last != null) {
            // Check if a new piece is actually needed or can the last one just be complemented => fewer pieces.
            val sameBg = last.background == bg

            if (last.content.isBlank() && sameBg) {
                last.foreground = fg
                last.mode = graphicsMode
                last.content += content.convert(graphicsMode)

            } else if (sameBg && content.isBlank()) {
                last.content += content.toString()

            } else if (sameBg && last.mode == graphicsMode && last.foreground == fg) {
                last.content += content.convert(graphicsMode)

            } else {
                startNewPiece = true
            }
        }

        if (startNewPiece) {
            pieces.add(PagePiece(fg, bg, graphicsMode, content.convert(graphicsMode)))
        }

        content.delete(0, content.length)
    }

    fun appendSpace() { content.append(if (hold) heldGraphicsChar else defaultSpaceChar) }

    fun resetHeldGraphicsChar() { heldGraphicsChar = defaultSpaceChar; hold = false }

    fun updateHeldGraphicsChar() { if (content.isNotEmpty() && graphicsMode != null) heldGraphicsChar = content.last() }

    for (str in strings) {
        val m = capturingTagPattern.matcher(str)

        if (m.matches()) {
            /**
             * String matched a tag that defines a spacing attribute.
             *
             * Only a subset of the spacing attributes are supported and it is likely that there are some errors in
             * how some are handled. Unsupported attributes are handled as a single space character.
             *
             * List of known spacing attributes that are unsupported (code, function name and tag name):
             *
             * 0x08 Flash           (flas)
             * 0x09 Steady          (stea)
             * 0x0A End Box         (ebox)
             * 0x0B Start Box       (sbox)
             * 0x0C Normal Size     (nhei?)
             * 0x0E Double Width    (?)         -- level >1.5
             * 0x0F Double Size     (?)         -- level >1.5
             * 0x1C Conceal         (?)
             * 0x1B ESC             (?)
             * 0x?? ?               (cdis)
             * 0x?? ?               (dle)
             *
             * The attributes are defined in Enhanced Teletext Specification (ETSI EN 300 706 v1.2.1).
             */
            val attr = m.group(1)

            if (attr == "SB") {
                // Start box
                // Not sure what this exactly is, but it seems to be used to produce a solid box:
                // symbol 0x3F in graphics mode and a bit smaller filled box in text mode.
                // Perhaps a separate tag is needed because the symbol would correspond with the DEL character (0x7F) (see String.toGraphicsChars)?
                content.append(graphicsMode?.range?.endInclusive?.toChar() ?: '■')
                updateHeldGraphicsChar()

            } else if (attr == "NB") {
                // 0x1D New Background. Immediate change.
                pieceBreak()
                appendSpace()
                bg = fg

            } else if (attr == "BB") {
                // 0x1C Black Background. Immediate change.
                pieceBreak()
                appendSpace()
                bg = null

            } else if (attr == "SG") {
                // 0x1A Separated Mosaic Graphics.
                pieceBreak()
                graphicsMode = GraphicsMode.SEPARATED
                appendSpace()

            } else if (attr == "CG") {
                // 0x19 Contiguous Mosaic Graphics
                pieceBreak()
                graphicsMode = GraphicsMode.CONTIGUOUS
                appendSpace()

            } else if (attr == "Hold") {
                // 0x1E Hold Mosaic. Instead of presenting a control with a space character use the last graphics symbol.
                hold = true
                appendSpace()

            } else if (attr == "Release") {
                // 0x1F Release Mosaic.
                appendSpace()
                resetHeldGraphicsChar()

            } else if (attr == "DH") {
                // 0x0D Double Height spec talks about stretching the chars and mosaics following the code, but
                // looking at the webpage version the whole line seems to get stretched - which makes more sense.
                // This interpretation also 'enables' ignoring the normal size attribute (nhei).
                doubleHeight = true
                appendSpace()
                pieceBreak()

            } else if (attr == "NH") {
                // Normal height
                doubleHeight = false
                appendSpace()
                pieceBreak()

            } else {
                val graphicsColor = attr.asGraphicsColor()

                if (graphicsColor != null) {
                    // 0x11-0x17 Mosaic Colour Codes
                    appendSpace()
                    pieceBreak()
                    if (graphicsMode == null) graphicsMode = GraphicsMode.CONTIGUOUS
                    fg = graphicsColor

                } else {
                    val textColor = attr.asColor()

                    if (textColor != null) {
                        // 0x01-0x07 Alpha Colour Codes
                        appendSpace()
                        pieceBreak()
                        graphicsMode = null
                        resetHeldGraphicsChar()
                        fg = textColor

                    } else {
                        // DW=Double width/DS=Double size/Conceal/ESC=Escape/Flash=Flash on/Steady=Flash off/EB=End box aren't supported.
                        appendSpace()
                        pieceBreak()
                    }
                }
            }

        } else {
            content.append(str)
            updateHeldGraphicsChar()
            pieceBreak()
        }
    }

    pieceBreak() // In case line ended with spacing attribute tags.
    pieces.firstOrNull()?.apply { lineStart = true }
    pieces.lastOrNull()?.apply {
        lineEnd = true

        // Lines in a teletext page are always the same width, but in the server
        // response the lines are trimmed. Without padding to full width the background
        // wouldn't be drawn correctly (bg fill would end after the last non-empty character).
        val totalContentLen = pieces.map { it.content.length }.sum()

        if (totalContentLen < MAX_CHARS_PER_LINE && bg != null) {
            val len = this.content.length + MAX_CHARS_PER_LINE - totalContentLen
            this.content = this.content.padEnd(len, ' ')
        }
    }
    pieces.forEach { it.doubleHeight = doubleHeight }
    return pieces
}

/**
 * Splits a single teletext line to strings that may start with a control tag.
 */
private fun splitByTags(line: String): List<String> {
    val pieces = ArrayList<String>()
    val m = tagPattern.matcher(line)
    var offset = 0

    while (m.find()) {

        if (m.start() > offset) {
            pieces.add(line.substring(offset, m.start()))
        }

        pieces.add(line.substring(m.start(), m.end()))
        offset = m.end()
    }

    if (offset < line.length) {
        pieces.add(line.substring(offset))
    }

    return pieces
}

private fun String.asGraphicsColor(): Color? {
    return takeIf { it.startsWith('G') && it.length > 1 }?.substring(1)?.asColor()
}

private fun String.asColor(): Color? {
    return when (this) {
        "Blue" -> Color.BLUE
        "White" -> Color.WHITE
        "Cyan" -> Color.CYAN
        "Green" -> Color(0.0f, 0.8f, 0.0f)
        "Yellow" -> Color.YELLOW
        "Red" -> Color.RED
        "Magenta" -> Color.MAGENTA
        else -> null
    }
}

private fun StringBuilder.convert(mode: GraphicsMode?): String {
    return mode?.convertChars(this) ?: toString()
}

private fun GraphicsMode.convertChars(sb: StringBuilder): String {
    val converted = StringBuilder(sb.length)

    for (c in sb.chars()) {

        if (c in 64..95) {

            val chr = when (c) {
                91 -> '<'
                92 -> '½'
                93 -> '>'
                94 -> '^'
                95 -> '#'
                else -> c.toChar() // 64-90 are a direct match with ASCII characters (@A-Z).
            }

            converted.append(chr)

        } else if (c < 128) {
            var v = c

            if (v > 95) {
                v -= 0x20
            }

            v -= 0x20
            v += range.first
            converted.append(v.toChar())

        } else if (c in BLOCK_SYMBOL_RANGE) {
            converted.append(c.toChar())

        } else {
            Log.error("Invalid character code $c")
        }
    }

    return converted.toString()
}
