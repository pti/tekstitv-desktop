package fi.reuna.tekstitv

import java.awt.Color
import java.awt.Graphics2D
import java.util.ArrayList
import java.util.regex.Pattern

/** Character code point offset for block symbols. */
const val BLOCK_SYMBOL_OFFSET_START = 0xE200
const val BLOCK_SYMBOL_OFFSET_END = 0xE240

class Piece(val foreground: Color,
            val background: Color?,
            val mode: GraphicsMode?,
            val content: String?,
            var lineEnd: Boolean = false)
{
    fun paint(g: Graphics2D, spec: PaintSpec, x: Int, y: Int): Int {

        if (content == null) {
            return 0
        }

        val contentWidth = spec.charWidth * content.length

        if (background != null) {
            g.color = background
            g.fillRect(x, y, contentWidth, spec.charHeight)
        }

        g.color = foreground
        g.font = spec.font
        val restoreTransform = g.transform

        if (mode != null) {
            var tx = x.toDouble()
            val ty = y.toDouble()

            for (code in content.chars()) {

                if (code in BLOCK_SYMBOL_OFFSET_START..(BLOCK_SYMBOL_OFFSET_END - 1)) {
                    val code = code - BLOCK_SYMBOL_OFFSET_START
                    val symbol = BlockSymbol.get(code, mode)

                    // Block symbol shapes are within coordinates (0,0) (1,1) so scale to match the font size.
                    g.transform = restoreTransform
                    g.translate(tx, ty)
                    g.scale(spec.charWidth.toDouble(), spec.charHeight.toDouble())

                    g.fill(symbol)

                    g.transform = restoreTransform

                } else {
                    g.drawString(code.toChar().toString(), tx.toInt(), y + spec.fontMetrics.ascent)
                }

                tx += spec.charWidth
            }

        } else {
            g.drawString(content, x, y + spec.fontMetrics.ascent)
        }

        return contentWidth
    }

    val isBlank: Boolean = content == null || content.isBlank()
}

/**
 * Converts teletext data received from the server to objects of type [Piece] that are then easier to handle while painting.
 *
 * The content consists of spacing attribute tags (3-4 characters inside square brackets) and characters.
 * Depending on the mode defined by a spacing attribute, characters can point to graphics mode symbols (block symbols)
 * or to normal text characters.
 *
 * The original content is split to lines (separated by newline-char). The result specifies line changes in [Piece.lineEnd].
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
fun pageContentToPieces(content: String): List<Piece> {
    val ret = ArrayList<Piece>()
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

private fun lineToPieces(line: String): List<Piece> {
    val strings = splitByTags(line)
    val pieces = ArrayList<Piece>(strings.size)

    val defaultFg: Color = Color.WHITE
    var bg: Color? = null
    var fg: Color = defaultFg
    var graphicsMode: GraphicsMode? = null
    var content = StringBuilder()

    fun startNewPiece() {

        if (!content.isEmpty()) {
            val str = content.toString()
            pieces.add(Piece(fg, bg, graphicsMode, if (graphicsMode != null) str.toGraphicsChars() else str))
            content.delete(0, content.length)
        }
    }

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
             * 0x0D Double Height   (dhei)
             * 0x0E Double Width    (?)         -- level >1.5
             * 0x0F Double Size     (?)         -- level >1.5
             * 0x1C Conceal         (?)
             * 0x1B ESC             (?)
             * 0x1F Release Mosaics (rgra)
             * 0x?? ?               (cdis)
             * 0x?? ?               (dle)
             *
             * The attributes are defined in Enhanced Teletext Specification (ETSI EN 300 706 v1.2.1).
             */
            val attr = m.group(1)
            val first = m.group(2)[0]

            // TODO handle hold graphics

            if (attr == "tbox") {
                // Not sure what this exactly is, but it seems to be used to produce a solid box (==symbol 0x3F in graphics mode).
                // Perhaps a separate tag is needed because the symbol would correspond with the DEL character (0x7F) (see String.toGraphicsChars)?

                if (graphicsMode == null) {
                    startNewPiece()
                    graphicsMode = GraphicsMode.CONNECTED
                }

                content.append(0x7F.toChar())

            } else if (attr == "nbgr") {
                // 0x1D New Background. Immediate change.
                startNewPiece()
                content.append(" ")
                bg = fg

            } else if (attr == "bbgr") {
                // 0x1C Black Background. Immediate change.
                startNewPiece()
                content.append(" ")
                bg = null

            } else if (attr == "sgra") {
                // 0x1A Separated Mosaic Graphics.
                startNewPiece()
                content.append(" ")
                graphicsMode = GraphicsMode.SEPARATED

            } else if (attr == "cgra") {
                // 0x19 Contiguous Mosaic Graphics
                startNewPiece()
                content.append(" ")
                graphicsMode = GraphicsMode.CONNECTED

            } else if (first == 'g') {
                startNewPiece()
                content.append(" ")
                graphicsMode = GraphicsMode.CONNECTED
                fg = colorForId(m.group(3)) ?: defaultFg

            } else if (first == 't') {
                content.append(" ")
                startNewPiece()
                graphicsMode = null
                fg = colorForId(m.group(3)) ?: defaultFg

            } else {
                content.append(" ")
                startNewPiece()
            }

        } else {
            content.append(str)
            startNewPiece()
        }
    }

    startNewPiece() // In case line ended with spacing attribute tags.
    pieces.lastOrNull()?.apply { lineEnd = true }
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

private fun String.toGraphicsChars(): String {
    val converted = StringBuilder(length)
    var c: Int

    for (ch in toCharArray()) {
        c = ch.toInt()

        if (c in 64..95) {

            val chr = when (c) {
                91 -> '<'
                92 -> '½'
                93 -> '>'
                94 -> '^'
                95 -> '#'
                else -> ch // 64-90 are a direct match with ASCII characters (@A-Z).
            }

            converted.append(chr)

        } else {

            if (c > 95) {
                c -= 0x20
            }

            c -= 0x20
            c += BLOCK_SYMBOL_OFFSET_START
            converted.append(c.toChar())
        }
    }

    return converted.toString()
}
