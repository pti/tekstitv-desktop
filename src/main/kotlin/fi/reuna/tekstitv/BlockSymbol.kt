package fi.reuna.tekstitv

import java.awt.Shape
import java.awt.geom.Area
import java.awt.geom.Rectangle2D

private val COL_X = doubleArrayOf(0.0, 0.5, 1.0)
private val COL_Y = doubleArrayOf(0.0, 0.3, 0.7, 1.0)
private const val SEPARATED_MARGIN_X = 0.12
private const val SEPARATED_MARGIN_Y = 0.06
private val SYMBOL_COUNT = BLOCK_SYMBOL_RANGE.last - BLOCK_SYMBOL_RANGE.first + 1
private val SEPARATED_OFFSET = GraphicsMode.SEPARATED.range.first - BLOCK_SYMBOL_RANGE.first

private class CombinedBlock(val value: Int, col: Int, row: Int, width: Int, height: Int) {
    val rect = Rectangle2D.Double(COL_X[col], COL_Y[row], COL_X[col + width] - COL_X[col], COL_Y[row + height] - COL_Y[row])
}

private val COMBINED_BLOCKS = listOf(
        // "horizontal" combined blocks (2 blocks per row).
        CombinedBlock(63, 0, 0, 2, 3),
        CombinedBlock(15, 0, 0, 2, 2),
        CombinedBlock(60, 0, 1, 2, 2),
        CombinedBlock(3, 0, 0, 2, 1),
        CombinedBlock(48, 0, 2, 2, 1),

        // "vertical" combined blocks (1 block per row).
        CombinedBlock(21, 0, 0, 1, 3),
        CombinedBlock(42, 1, 0, 1, 3),
        CombinedBlock(5, 0, 0, 1, 2),
        CombinedBlock(10, 1, 0, 1, 2),
        CombinedBlock(20, 0, 1, 1, 2),
        CombinedBlock(40, 1, 1, 1, 2)
)

object BlockSymbol {

    private val symbols = Array(SYMBOL_COUNT) { i -> create(i) }

    fun get(code: Int): Shape {
        return symbols[code]
    }

    private fun create(code: Int): Shape {
        // BlockSymbols consists of 0-6 blocks in a 2x3 grid.
        // Symbols can be either in connected mode (no space between blocks) or separated mode (bit of space between blocks).
        // Blocks are numbered in row-major-order. Blocks 0-1 are for the top row, 2-3 middle and 4-5 bottom row.
        // 64 different symbols for both modes. The code is a bitmask specifying what blocks to fill.
        // 0b1: fill block #0, 0b10: fill block #1, ...
        val isSeparated = code >= SEPARATED_OFFSET
        val symbol = Area()
        var codeBits = code - if (isSeparated) SEPARATED_OFFSET else 0x0

        if (!isSeparated) {
            // Check if the symbol has rectangle shaped blocks with area >1. Those can be painted with a single rectangle.
            for (combinedBlock in COMBINED_BLOCKS) {

                if (codeBits and combinedBlock.value == combinedBlock.value) {
                    symbol.add(Area(combinedBlock.rect))
                    codeBits -= combinedBlock.value
                }
            }
        }

        for (i in 0..5) {

            if (codeBits and 1 == 1) {
                val col = i % 2
                val row = i / 2

                var x0 = COL_X[col]
                val y0 = COL_Y[row]
                val x1 = COL_X[col + 1]
                var y1 = COL_Y[row + 1]

                if (isSeparated) {
                    x0 += SEPARATED_MARGIN_X
                    y1 -= SEPARATED_MARGIN_Y
                }

                val block = Rectangle2D.Double(x0, y0, x1 - x0, y1 - y0)
                symbol.add(Area(block))
            }

            codeBits = codeBits shr 1
        }

        return symbol
    }
}
