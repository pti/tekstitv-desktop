package fi.reuna.tekstitv

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
    data class Loaded(val subpage: Subpage, val cached: Boolean = false) : PageEvent()
    data class Failed(val type: ErrorType, val error: Throwable?, val location: Location, val autoReload: Boolean) : PageEvent()
}

data class Page(val number: Int, val subpages: List<Subpage>) {

    // Subpage numbers in the server response start from 1.
    // Subpage numbers aren't necessarily continuous / start from 1 => use indexes as subpage numbers instead.
    constructor(src: TTVPage) : this(src.number, src.subpages.mapIndexed { index, sub -> Subpage(Location(src.number, index), sub.content) })

    fun getSubpage(index: Int): Subpage? {
        return if (index >= 0 && index < subpages.size) subpages[index] else null
    }
}

data class Subpage(val location: Location, val content: String) {

    val pieces: Array<PagePiece> = pageContentToPieces(content).toTypedArray()

}
