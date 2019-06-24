package fi.reuna.tekstitv

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import retrofit2.HttpException
import java.time.Duration
import java.time.Instant
import java.util.*

private data class CacheEntry(val page: Page, val added: Instant = Instant.now())

enum class Direction(val delta: Int) {
    NEXT(+1),
    PREV(-1);

    override fun toString(): String = name.toLowerCase()
}

interface PageEventListener {
    fun onPageEvent(event: PageEvent)
}

class PageProvider(private val listener: PageEventListener): CoroutineScope by CoroutineScope(Dispatchers.Main) {

    private val history = Stack<Location>()
    private val cache = mutableMapOf<Int, CacheEntry>()

    private var relativeRequests: Channel<Direction>? = null
    private var relativeConsumer: Job? = null

    val currentLocation: Location
        get() = history.lastOrNull() ?: Location(100, 0)

    val currentPage: Subpage?
        get() = currentLocation.fromCache()


    fun stop() {
        cancel()
        TTVService.shutdown()
    }

    fun set(location: Location, checkCache: Boolean = true, autoReload: Boolean = false) {
        // Relative requests aren't relevant after setting an absolute position, so empty the queue + stop processing the reqs.
        stopRelativeRequestConsumer()

        val cached = if (checkCache) location.checkCache() else null

        if (cached != null) {
            handleCacheHit(cached)
            return
        }

        launch { get(location, autoReload = autoReload) }
    }

    fun set(page: Int) {
        set(Location(page, 0))
    }

    fun reload(autoReload: Boolean = false) {
        set(currentLocation, checkCache = false, autoReload = autoReload)
    }

    fun back() {

        if (history.size > 1) {
            history.pop()
            set(currentLocation)
        }
    }

    fun togglePrevious() {

        if (history.size > 1) {
            val current = history.pop()
            val previous = history.pop()
            history.push(current)
            set(previous)
        }
    }

    fun nextPage() {
        sendRelativeRequest(Direction.NEXT)
    }

    fun prevPage() {
        sendRelativeRequest(Direction.PREV)
    }

    fun nextSubpage() {
        setSubpage(Direction.NEXT)
    }

    fun prevSubpage() {
        setSubpage(Direction.PREV)
    }

    fun setSubpage(direction: Direction) {
        val page = cache[currentLocation.page]?.page

        if (page != null) {
            val numSubs = page.subpages.size
            var newSubpage = (currentLocation.sub + direction.delta) % numSubs
            if (newSubpage < 0) newSubpage = numSubs - 1

            // Instead of adding another instance of the current page to the history stack, replace page's current instance with updated subpage. Quicker to move backwards in history this way.
            currentLocation.withSub(newSubpage).fromCache()?.let {
                history.pop()
                history.push(it.location)
                notify(PageEvent.Loaded(it, cached = true))
            }
        }
    }

    private fun handleCacheHit(cached: Subpage) {
        Log.debug("cached ${cached.location.page}")
        historyAdd(cached.location)
        notify(PageEvent.Loaded(cached, cached = true))
    }

    private fun handle(received: TTVContent, ref: Location, autoReload: Boolean): PageEvent {
        val pages = received.pages.map { Page(it) }
        pages.forEach { cache[it.number] = CacheEntry(it) }
        val sub = pages.firstOrNull()?.getSubpage(ref.sub)

        if (!autoReload && sub != null) {
            historyAdd(sub.location)
        }

        return when (sub) {
            null -> PageEvent.Failed(ErrorType.NOT_FOUND, null, ref, autoReload)
            else -> PageEvent.Loaded(sub)
        }
    }

    private fun historyAdd(location: Location) {

        if (history.isEmpty() || history.peek().page != location.page) {
            history.push(location)
        }
    }

    private fun Location.fromCache(): Subpage? {
        return cache[page]?.page?.getSubpage(sub)
    }

    private fun Location.checkCache(): Subpage? {
        var cacheEntry = cache[page]

        if (cacheEntry != null && cacheEntry.added.since().toMinutes() > 10) {
            cache.remove(page)
            cacheEntry = null
        }

        return cacheEntry?.page?.getSubpage(sub)
    }

    private fun Instant.since(): Duration {
        return Duration.between(this, Instant.now())
    }

    private fun Throwable.asPageEvent(location: Location, autoReload: Boolean): PageEvent {
        var type = ErrorType.OTHER

        if (this is HttpException && code() == 404) {
            type = ErrorType.OTHER
        }

        return PageEvent.Failed(type, this, location, autoReload)
    }

    private fun notify(event: PageEvent) {
        listener.onPageEvent(event)
    }

    private suspend fun get(location: Location, direction: Direction? = null, autoReload: Boolean = false, notFoundLocation: Location? = null) {

        try {
            Log.debug("get ${location.page}")
            val resp = TTVService.instance.getPage(location.page, direction)
            val event = handle(resp, location, autoReload)

            // Ignore the auto reload response if current location has changed.
            if (!autoReload || event !is PageEvent.Loaded || event.subpage.location == currentLocation) {
                notify(event)
            }

        } catch (ce: CancellationException) {
            Log.debug("cancelled - ignore")

        } catch (t: Throwable) {

            if (t is HttpException && t.code() == 404 && notFoundLocation != null) {
                get(notFoundLocation)

            } else {
                Log.error("Failed to get page ${location.page}: $t")
                historyAdd(location)
                notify(t.asPageEvent(location, autoReload))
            }
        }
    }

    private suspend fun consumeRelativeRequests() {
        val channel = relativeRequests

        while (isActive && channel != null) {
            val direction = channel.receive()
            val relativeTo = currentLocation
            Log.info("relative to ${relativeTo.page}.${relativeTo.sub} move ${direction.delta}")

            // TODO some kind of linked list to cache so that we can check for other than just the immediate page
            val hit = relativeTo.move(direction).checkCache()

            if (hit != null) {
                handleCacheHit(hit)
                continue
            }

            get(relativeTo, direction, notFoundLocation = relativeTo.move(direction))
            // If the relativeTo points to a non-existing page, then nextPage/prevPage requests always fail.
            // If that happens, simply try to request to the nextPage page by number. This is useful in case
            // the user has entered an invalid page and then attempts to move to nextPage/prevPage page.
        }
    }

    private fun sendRelativeRequest(direction: Direction) {
        // User might press next/prev repeatedly. If one would just do the request directly, then the same page
        // request might get triggered. To avoid that and to allow the user to move multiple pages by pressing
        // next/prev rapidly, a queue is needed for the next/prev requests. Consume a request at a time.
        Log.debug("$direction")
        if (relativeConsumer == null) startRelativeRequestConsumer()
        launch { relativeRequests?.send(direction) }
    }

    private fun startRelativeRequestConsumer() {
        Log.debug("start")
        relativeRequests = Channel()
        relativeConsumer = launch { consumeRelativeRequests() }
    }

    private fun stopRelativeRequestConsumer() {

        if (relativeConsumer != null) {
            Log.debug("stop")
            relativeConsumer?.cancel()
            relativeConsumer = null

            relativeRequests?.cancel()
            relativeRequests = null
        }
    }
}
