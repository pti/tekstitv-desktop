package fi.reuna.tekstitv

import java.awt.Color
import java.awt.Rectangle
import java.time.Instant

data class Location(val page: Int, val sub: Int) {

    fun move(direction: Direction): Location {
        return Location(page + direction.delta, 0)
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

    /** Loading is followed by either Loaded, Failed or Ignored. */
    data class Loading(val req: PageRequest) : PageEvent()

    data class Ignored(val req: PageRequest) : PageEvent()

    data class Loaded(val subpage: Subpage, val cached: Boolean = false, val noChange: Boolean = false) : PageEvent() {
        val location = subpage.location
    }

    data class Failed(val type: ErrorType, val error: Throwable?, val req: PageRequest) : PageEvent()
}

data class PageRequest(val location: Location, val direction: Direction? = null, val refresh: Boolean = false)

class PageRequestException(status: Int, message: String?, val request: PageRequest): HttpException(status, message)

data class Page(val number: Int, val subpages: List<Subpage>) {

    // Subpage numbers in the server response start from 1.
    // Subpage numbers aren't necessarily continuous / start from 1 => use indexes as subpage numbers instead.
    constructor(src: TTVPage) : this(src.number, src.subpages.mapIndexed { index, sub -> Subpage(Location(src.number, index), sub.content, sub.timestamp) })

    fun getSubpage(index: Int): Subpage? {
        return if (index >= 0 && index < subpages.size) subpages[index] else null
    }
}

const val CHARS_IN_LINK = 3
const val INVALID_PAGE = 0
const val MIN_PAGE = 100
const val MAX_PAGE = 899
val PAGE_RANGE = MIN_PAGE..MAX_PAGE

data class Subpage(val location: Location, val content: String, val timestamp: Instant?) {

    val pieces: Array<PagePiece> = pageContentToPieces(content).toTypedArray()
    val uniqueLinks: IntArray
    val allLinks: Array<PageLink>

    init {
        val links = parseLinks(pieces)
        uniqueLinks = links.first
        allLinks = links.second
    }

    /**
     * Coordinates are in 'block-coordinates' / character coordinates. Top left corner is (0,0) and bottom right corner
     * is (39,22).
     */
    fun findLink(x: Int, y: Int): PageLink? {

        for (link in allLinks) {

            if (link.rect.contains(x, y)) {
                return link
            }
        }

        return null
    }

    val emptyFirstColumn = pieces
            .filter { it.lineStart && it.content.isNotEmpty() }
            .map { it.background ?: Color.BLACK == Color.BLACK && it.content.first().isEmptySymbol(it.mode) }
            .reduce { a, b -> a && b }
}

private fun Char.isEmptySymbol(mode: GraphicsMode?): Boolean {
    return (this == ' ' || (mode != null && (this.toInt() - BLOCK_SYMBOL_OFFSET_START) == 0))
}

private val PAGE_NUM_REGEX = """(?=(?:\s|-|>|^)([1-8]\d{2})(?:\s|\. |, |-|!|$))""".toRegex()

private fun parseLinks(pieces: Array<PagePiece>): Pair<IntArray, Array<PageLink>> {
    val defaultColor = Color.WHITE
    val all = arrayListOf<PageLink>()
    val unique = arrayListOf<PageLink>()
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

    var y = 0
    var x = 0

    for ((index, piece) in pieces.withIndex()) {
        val pieceImportance: Int by lazy { importance(piece, index) }

        for (match in PAGE_NUM_REGEX.findAll(piece.content)) {
            val group = match.groups[1]!!
            val pageNumber = group.value.toInt()

            // Prefer numbers defined at the start of the line.
            val positionImportance = if (piece.lineStart && match.groups[1]?.range?.start == 0) 1 else 0
            val importance = pieceImportance + positionImportance

            val left = x + group.range.first
            val link = PageLink(pageNumber, importance, Rectangle(left, y, CHARS_IN_LINK, if (piece.doubleHeight) 2 else 1))
            all.add(link)

            val existing = unique.firstOrNull { it.page == pageNumber }

            if (existing == null) {
                unique.add(link)

            } else if (existing.importance < importance) {
                existing.importance = importance
            }
        }

        if (piece.lineEnd) {
            x = 0
            y += if (piece.doubleHeight) 2 else 1
        } else {
            x += piece.content.length
        }
    }

    val sorted = unique.toList()
            .sortedByDescending { it.importance }
            .map { it.page }
            .toIntArray()
    return Pair(sorted, all.toTypedArray())
}

data class PageLink(val page: Int, var importance: Int, val rect: Rectangle)
