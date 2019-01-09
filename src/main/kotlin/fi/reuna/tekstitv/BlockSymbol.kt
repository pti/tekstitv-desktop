package fi.reuna.tekstitv

import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Rectangle2D

private val COL_X = doubleArrayOf(0.0, 0.5, 1.0)
private val COL_Y = doubleArrayOf(0.0, 0.3, 0.7, 1.0)
private const val SEPARATED_MARGIN_X = 0.12
private const val SEPARATED_MARGIN_Y = 0.06
private const val SYMBOL_COUNT = 64

enum class GraphicsMode {
    CONNECTED,
    SEPARATED
}

object BlockSymbol {

    private val connectedSymbols = Array(SYMBOL_COUNT) { i -> create(i, GraphicsMode.CONNECTED) }
    private val separatedSymbols = Array(SYMBOL_COUNT) { i -> create(i, GraphicsMode.SEPARATED) }

    fun get(code: Int, mode: GraphicsMode): Shape {
        return if (mode == GraphicsMode.SEPARATED) separatedSymbols[code] else connectedSymbols[code]
    }

    private fun create(code: Int, mode: GraphicsMode): Shape {
        // BlockSymbols consists of 0-6 blocks in a 2x3 grid.
        // Symbols can be either in connected mode (no space between blocks) or separated mode (bit of space between blocks).
        // Blocks are numbered in row-major-order. Blocks 0-1 are for the top row, 2-3 middle and 4-5 bottom row.
        // 64 different symbols for both modes. The code is a bitmask specifying what blocks to fill.
        // 0b1: fill block #0, 0b10: fill block #1, ...
        val symbol = Area()
        var code = code

        for (i in 0..5) {

            if (code and 1 == 1) {
                val col = i % 2
                val row = i / 2

                var x0 = COL_X[col]
                val y0 = COL_Y[row]
                val x1 = COL_X[col + 1]
                var y1 = COL_Y[row + 1]

                if (mode == GraphicsMode.SEPARATED) {
                    x0 += SEPARATED_MARGIN_X
                    y1 -= SEPARATED_MARGIN_Y
                }

                val block = Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0)
                symbol.add(Area(block))
            }

            code = code shr 1
        }

        return symbol
    }
}
