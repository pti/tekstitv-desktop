package fi.reuna.tekstitv

data class Location(val page: Int, val sub: Int) {

    fun move(direction: Direction): Location {
        return Location(page + direction.delta, 0)
    }

    fun moveSubpage(delta: Int = 0): Location {
        return Location(page, sub + delta)
    }
}

sealed class PageEvent {
    data class Loaded(val subpage: Subpage) : PageEvent()
    data class NotFound(val location: Location? = null) : PageEvent()
    data class Failed(val error: Throwable, val location: Location? = null) : PageEvent()
}

data class Page(val number: Int, val subpages: List<Subpage>) {

    // Subpage numbers in the server response start from 1.
    constructor(src: TTVPage) : this(src.number, src.subpages.map { Subpage(Location(src.number, it.number - 1), it.content) })

    fun getSubpage(index: Int): Subpage? {
        return if (index >= 0 && index < subpages.size) subpages[index] else null
    }
}

data class Subpage(val location: Location, val content: String) {

    val pieces: Array<PagePiece> = pageContentToPieces(content).toTypedArray()

}
