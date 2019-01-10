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
            val lineEnd: Boolean)
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

        if (mode != null) {
            val restoreTransform = g.transform
            var tx = x.toDouble()
            val ty = y.toDouble()

            for (char in content.chars()) {

                if (char in BLOCK_SYMBOL_OFFSET_START..(BLOCK_SYMBOL_OFFSET_END - 1)) {
                    val code = char - BLOCK_SYMBOL_OFFSET_START
                    val symbol = BlockSymbol.get(code, mode)

                    // Block symbol shapes are within coordinates (0,0) (1,1) so scale to match the font size.
                    g.transform = restoreTransform
                    g.translate(tx, ty)
                    g.scale(spec.charWidth.toDouble(), spec.charHeight.toDouble())

                    g.fill(symbol)

                    g.transform = restoreTransform

                } else {
                    g.drawString(char.toString(), tx.toInt(), y + spec.fontMetrics.ascent)
                }

                tx += spec.charWidth
            }

        } else {
            g.drawString(content, x, y + spec.fontMetrics.ascent)
        }

        return contentWidth
    }
}

/**
 * Converts teletext data received from the server to objects of type [Piece] that are then easier to handle while painting.
 *
 * The content consists of control tags (4 characters inside square brackets) and characters.
 * Depending on the mode defined by a control-string, characters can point to graphics mode symbols (block symbols)
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
    val lines = content.split("\n")

    for (line in lines) {
        ret += lineToPieces(line)
    }

    return ret
}

private val tagPattern = Pattern.compile("\\[[a-z]{4}\\]")
private val capturingTagPattern = Pattern.compile("^\\[(([a-z])([a-z]{3}))\\]$")

private fun String.lastChar(): String? {
    return if (length > 0) substring(length - 1) else null
}

private fun lineToPieces(line: String): List<Piece> {
    val strings = splitByTags(line)
    val pieces = ArrayList<Piece>(strings.size)

    var bg: Color? = null
    var fg = Color.WHITE
    var graphicsMode: GraphicsMode? = null
    var heldGraphicsChar: String? = " "
    var holdGraphicsChar = false
    var content: String?
    var piece: Piece? = null

    for ((index, str) in strings.withIndex()) {
        val m = capturingTagPattern.matcher(str)
        val lineEnd = index == strings.size - 1

        if (m.matches()) {
            content = heldGraphicsChar

            val control = m.group(1)
            val first = m.group(2)[0]
            var fgAfter: Color? = null
            var releaseHeldGraphicsChar = false

            if (control == "tbox") {
                // Not sure what this exactly is, but it seems to be used to produce a solid box (==symbol 127 in graphics mode).
                content = (BLOCK_SYMBOL_OFFSET_END - 1).toString()

            } else if (control == "nbgr") {
                // New background
                bg = fg

            } else if (control == "bbgr") {
                // Seems to be used for clearing the background color.
                bg = null

            } else if (control == "hgra") {
                // Hold graphics - instead of presenting a control with a space character use the last graphics symbol.
                holdGraphicsChar = true
                heldGraphicsChar = null

                if (!pieces.isEmpty()) {
                    val last = pieces[pieces.size - 1]
                    heldGraphicsChar = last.content?.lastChar()
                }

                if (heldGraphicsChar == null) {
                    heldGraphicsChar = " "
                }

                content = heldGraphicsChar

            } else if (control == "sgra") {
                graphicsMode = GraphicsMode.SEPARATED

            } else if (first == 'g') {
                graphicsMode = GraphicsMode.CONNECTED
                fgAfter = colorForId(m.group(3))
                releaseHeldGraphicsChar = true

            } else if (first == 't') {
                holdGraphicsChar = false
                graphicsMode = null
                fgAfter = colorForId(m.group(3))
                releaseHeldGraphicsChar = true
            }

            pieces.add(Piece(fg, bg, graphicsMode, content, lineEnd))

            if (fgAfter != null) {
                fg = fgAfter
            }

            if (releaseHeldGraphicsChar) {
                heldGraphicsChar = " "
            }

        } else {
            content = if (graphicsMode != null) str.toGraphicsChars() else str
            val piece = Piece(fg, bg, graphicsMode, content, lineEnd)
            pieces.add(piece)

            if (graphicsMode != null && holdGraphicsChar) {
                heldGraphicsChar = piece.content?.lastChar()
            }
        }
    }

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

    for (i in 0 until length) {
        c = this[i].toInt()

        if (c in 64..95) {

            val ch = when (c) {
                91 -> '<'
                92 -> '½'
                93 -> '>'
                94 -> '^'
                95 -> '#'
                else ->
                    // 64-90 are a direct match with ASCII characters (@A-Z).
                    c.toChar()
            }

            c = ch.toInt()

        } else {

            if (c > 95) {
                c -= 32
            }

            c -= 32
            c += BLOCK_SYMBOL_OFFSET_START
        }

        converted.append(c.toChar())
    }

    return converted.toString()
}
