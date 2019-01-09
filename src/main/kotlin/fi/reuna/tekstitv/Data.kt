package fi.reuna.tekstitv

data class Location(val page: Int, val sub: Int) {

    fun move(direction: Direction): Location {
        return Location(page + direction.delta, 0)
    }

    fun subpage(delta: Int = 0): Location {
        return Location(page, sub + delta)
    }
}

sealed class PageEvent {
    data class Loaded(val page: Page) : PageEvent()
    data class NotFound(val location: Location? = null) : PageEvent()
    data class Failed(val error: Throwable, val location: Location? = null) : PageEvent()
}

data class Page(val location: Location, val content: String)
