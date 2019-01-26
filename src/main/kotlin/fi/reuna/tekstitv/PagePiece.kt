package fi.reuna.tekstitv

import java.awt.Color
import java.awt.Graphics2D
import java.util.*
import java.util.regex.Pattern

/** Character code point offset for block symbols. */
const val BLOCK_SYMBOL_OFFSET_START = 0xE200
const val BLOCK_SYMBOL_OFFSET_END = 0xE27F

val BLOCK_SYMBOL_RANGE = BLOCK_SYMBOL_OFFSET_START..BLOCK_SYMBOL_OFFSET_END

enum class GraphicsMode(val range: IntRange) {
    CONTIGUOUS(0xE200..0xE23F),
    SEPARATED(0xE240..0xE27F)
}

class PagePiece(var foreground: Color,
                var background: Color?,
                var mode: GraphicsMode?,
                var content: String,
                var lineEnd: Boolean = false,
                var doubleHeight: Boolean = false)
{
    fun prepend(content: String) {
        this.content = content + this.content
    }

    fun paint(g: Graphics2D, spec: PaintSpec, x: Int, y: Int): Int {

        if (content.isEmpty()) {
            return 0
        }

        val multiplier = if (doubleHeight) 2 else 1
        val contentWidth = spec.charWidth * content.length

        if (background != null) {
            g.color = background
            g.fillRect(x, y, contentWidth, spec.charHeight * multiplier)
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
fun pageContentToPieces(content: String): List<PagePiece> {
    val ret = ArrayList<PagePiece>()
    val lines = content.split("\n").dropLastWhile { it.isEmpty() } // Drop the last empty string produced by split().

    for (line in lines) {
        ret += lineToPieces(line)
    }

    return ret
}

private val tagPattern = Pattern.compile("\\[[a-z]{3,4}\\]")
private val capturingTagPattern = Pattern.compile("^\\[(([a-z])([a-z]{2,3}))\\]$")

private fun String.lastChar(): String? {
    return if (length > 0) substring(length - 1) else null
}

private fun lineToPieces(line: String): List<PagePiece> {
    val strings = splitByTags(line)
    val pieces = ArrayList<PagePiece>(strings.size)

    val defaultFg: Color = Color.WHITE
    var bg: Color? = null
    var fg: Color = defaultFg
    var graphicsMode: GraphicsMode? = null
    var content = StringBuilder()
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

    fun updateHeldGraphicsChar() { if (content.isNotEmpty()) heldGraphicsChar = content.last() }

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
            val first = m.group(2)[0]

            if (attr == "tbox") {
                // Not sure what this exactly is, but it seems to be used to produce a solid box:
                // symbol 0x3F in graphics mode and a bit smaller filled box in text mode.
                // Perhaps a separate tag is needed because the symbol would correspond with the DEL character (0x7F) (see String.toGraphicsChars)?
                content.append(graphicsMode?.range?.endInclusive?.toChar() ?: '■')
                updateHeldGraphicsChar()

            } else if (attr == "nbgr") {
                // 0x1D New Background. Immediate change.
                pieceBreak()
                appendSpace()
                bg = fg

            } else if (attr == "bbgr") {
                // 0x1C Black Background. Immediate change.
                pieceBreak()
                appendSpace()
                bg = null

            } else if (attr == "sgra") {
                // 0x1A Separated Mosaic Graphics.
                pieceBreak()
                graphicsMode = GraphicsMode.SEPARATED
                appendSpace()

            } else if (attr == "cgra") {
                // 0x19 Contiguous Mosaic Graphics
                pieceBreak()
                graphicsMode = GraphicsMode.CONTIGUOUS
                appendSpace()

            } else if (attr == "hgra") {
                // 0x1E Hold Mosaic. Instead of presenting a control with a space character use the last graphics symbol.
                hold = true
                appendSpace()

            } else if (attr == "rgra") {
                // 0x1F Release Mosaic.
                appendSpace()
                resetHeldGraphicsChar()

            } else if (attr == "dhei") {
                // 0x0D Double Height spec talks about stretching the chars and mosaics following the code, but
                // looking at the webpage version the whole line seems to get stretched - which makes more sense.
                // This interpretation also 'enables' ignoring the normal size attribute (nhei).
                doubleHeight = true
                appendSpace()
                pieceBreak()

            } else if (first == 'g') {
                // 0x11-0x17 Mosaic Colour Codes
                appendSpace()
                pieceBreak()
                if (graphicsMode == null) graphicsMode = GraphicsMode.CONTIGUOUS
                fg = colorForId(m.group(3)) ?: defaultFg

            } else if (first == 't') {
                // 0x01-0x07 Alpha Colour Codes
                appendSpace()
                pieceBreak()
                graphicsMode = null
                resetHeldGraphicsChar()
                fg = colorForId(m.group(3)) ?: defaultFg

            } else {
                appendSpace()
                pieceBreak()
            }

        } else {
            content.append(str)
            updateHeldGraphicsChar()
            pieceBreak()
        }
    }

    pieceBreak() // In case line ended with spacing attribute tags.
    pieces.lastOrNull()?.apply { lineEnd = true }
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

    if (offset < line.length - 1) {
        pieces.add(line.substring(offset))
    }

    return pieces
}

private fun colorForId(colorId: String): Color? {
    return when (colorId) {
        "blu" -> Color.BLUE
        "whi" -> Color.WHITE
        "cya" -> Color.CYAN
        "gre" -> Color(0.0f, 0.8f, 0.0f)
        "yel" -> Color.YELLOW
        "red" -> Color.RED
        "mag" -> Color.MAGENTA
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
            v += range.start
            converted.append(v.toChar())

        } else if (c in BLOCK_SYMBOL_RANGE) {
            converted.append(c.toChar())

        } else {
            Log.error("Invalid character code $c")
        }
    }

    return converted.toString()
}
