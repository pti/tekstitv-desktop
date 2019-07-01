package fi.reuna.tekstitv

import java.awt.Color
import java.time.Instant

data class Location(val page: Int, val sub: Int) {

    fun move(direction: Direction): Location {
        return Location(page + direction.delta, 0)
    }

    fun moveSubpage(delta: Int = 0): Location {
        return Location(page, sub + delta)
    }

    fun withSub(sub: Int): Location {
        return Location(page, sub)
    }
}

enum class ErrorType {
    NOT_FOUND,
    OTHER
}

sealed class PageEvent {
    data class Loaded(val subpage: Subpage, val cached: Boolean = false, val noChange: Boolean = false) : PageEvent()
    data class Failed(val type: ErrorType, val error: Throwable?, val location: Location, val autoReload: Boolean) : PageEvent()
}

data class Page(val number: Int, val subpages: List<Subpage>) {

    // Subpage numbers in the server response start from 1.
    // Subpage numbers aren't necessarily continuous / start from 1 => use indexes as subpage numbers instead.
    constructor(src: TTVPage) : this(src.number, src.subpages.mapIndexed { index, sub -> Subpage(Location(src.number, index), sub.content, sub.timestamp) })

    fun getSubpage(index: Int): Subpage? {
        return if (index >= 0 && index < subpages.size) subpages[index] else null
    }
}

data class Subpage(val location: Location, val content: String, val timestamp: Instant?) {

    val pieces: Array<PagePiece> = pageContentToPieces(content).toTypedArray()
    val links: IntArray = parseLinks(pieces)

    val emptyFirstColumn = pieces
            .filter { it.lineStart && it.content.isNotEmpty() }
            .map { it.background ?: Color.BLACK == Color.BLACK && it.content.first().isEmptySymbol(it.mode) }
            .reduce { a, b -> a && b }
}

private fun Char.isEmptySymbol(mode: GraphicsMode?): Boolean {
    return (this == ' ' || (mode != null && (this.toInt() - BLOCK_SYMBOL_OFFSET_START) == 0))
}

private fun parseLinks(pieces: Array<PagePiece>): IntArray {
    val defaultColor = Color.WHITE
    val links = arrayListOf<PageLink>()
    val pageNumRegex = """(?=(?:\s|-|^)([1-8]\d{2})(?:\s|. |-|$))""".toRegex()
    // Lookahead is used to allow overlapping matches, e.g. in "331   345-346" match 331, 345 and 346.

    fun importance(piece: PagePiece, index: Int): Int {
        val next = if (index < pieces.size - 1) pieces[index + 1] else null

        return when {
            piece.doubleHeight && piece.foreground != defaultColor && piece.background == null -> 100
            piece.doubleHeight -> 90
            next != null && piece.content.trim().length == 3 -> {
                if (piece.foreground != next.foreground && next.foreground != defaultColor) 10 else 1
            }
            else -> 1
        }
    }

    for ((index, piece) in pieces.withIndex()) {
        val pieceImportance: Int by lazy { importance(piece, index) }

        for (match in pageNumRegex.findAll(piece.content.trimStart())) {
            val pageNumber = match.groupValues[1].toInt()

            // Prefer numbers defined at the start of the line.
            val positionImportance = if (piece.lineStart && match.groups[1]?.range?.start == 0) 1 else 0
            val importance = pieceImportance + positionImportance

            val existing = links.firstOrNull { it.page == pageNumber }

            if (existing == null) {
                links.add(PageLink(pageNumber, importance))

            } else if (existing.importance < importance) {
                existing.importance = importance
            }
        }
    }

    return links.toList()
            .sortedByDescending { it.importance }
            .map { it.page }
            .toIntArray()
}

data class PageLink(val page: Int, var importance: Int)
