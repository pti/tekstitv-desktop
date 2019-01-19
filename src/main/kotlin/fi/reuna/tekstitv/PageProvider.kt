package fi.reuna.tekstitv

import io.reactivex.Observable
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import java.time.Duration
import java.time.Instant
import java.util.*

private data class CacheEntry(val page: Page, val added: Instant = Instant.now())

enum class Direction(val delta: Int) {
    NEXT(+1),
    PREV(-1)
}

class PageProvider {

    private val history = Stack<Location>()
    private val cache = mutableMapOf<Int, CacheEntry>()
    private val observable: Observable<PageEvent>
    private val pageEventSubject = BehaviorSubject.create<PageEvent>()
    private val relativeSubject = PublishSubject.create<Direction>()
    private val scheduler = Schedulers.single()
    private val disposables = CompositeDisposable()

    // TODO thread safety

    val currentLocation: Location
        get() = history.lastOrNull() ?: Location(100, 0)

    val currentPage: Subpage?
        get() = currentLocation.fromCache()

    init { 
        observable = pageEventSubject

        disposables += relativeSubject
                .observeOn(scheduler)
                .subscribe { direction ->
                    val ttv = TTVService.instance
                    val relativeTo = currentLocation
                    Log.info("relative to ${relativeTo.page}.${relativeTo.sub} move ${direction.delta}")

                    if (direction == null) {
                        return@subscribe
                    }

                    // TODO some kind of linked list to cache so that we can check for other than just the immediate page
                    relativeTo.move(direction).checkCache()?.let {
                        handleCacheHit(it)
                        return@subscribe
                    }

                    when (direction) {
                        Direction.NEXT -> ttv.getNextPage(relativeTo.page)
                        Direction.PREV -> ttv.getPreviousPage(relativeTo.page)
                    }
                        // If the currentLocation points to a non-existing page, then nextPage/prevPage requests always fail. If that happens, simply try to request to the nextPage page by number.
                        // This is useful in case the user has entered an invalid page and then attempts to move to nextPage/prevPage page.
                        .onErrorResumeNext { ttv.getPage(relativeTo.page + direction.delta) }
                        .map { handle(it, relativeTo.withSub(0)) }
                        .onErrorReturn { PageEvent.Failed(it, relativeTo) }
                        .doOnSuccess { pageEventSubject.onNext(it) }
                        .subscribe()
                }
    }

    fun stop() {
        TTVService.shutdown()
        disposables.dispose()
    }

    // TODO currentLocation and history etc handling needs to be done thread safely - mutex?

    // TODO when using cached always trigger a reload too to see if the page has changed -- at least if old enough (enough time has passed since the last check)

    fun observe(): Observable<PageEvent> {
        return observable
    }

    fun set(location: Location, checkCache: Boolean = true) {
    // TODO relativeSubject should be stopped (completed?) - setting a position explicitly should override relative movement
    //      or relative events before a certain timestamp should be ignored
        val cached = if (checkCache) location.checkCache() else null

        if (cached != null) {
            handleCacheHit(cached)
            return
        }

        TTVService.instance.getPage(location.page)
                .observeOn(scheduler)
                .map { handle(it, location) }
                .onErrorReturn {
                    historyAdd(location)
                    PageEvent.Failed(it, location)
                }
                .doOnSuccess { pageEventSubject.onNext(it) }
                .subscribe()
    }

    fun set(page: Int) {
        set(Location(page, 0))
    }

    fun reload() {
        set(currentLocation, checkCache = false)
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
        Log.info("nextPage")
        // Handle nextPage/prevPage synchronously instead of immediately -> user can press nextPage/prevPage quickly N times to move N pages forward/backward (while taking possible gaps into account).
        // Otherwise quick nextPage/prevPage presses would result in requesting the same page multiple times (depending of course on the network+server response times and how rapid the navigation is).
        // Setting the page immediately is useful in case a request takes a long time etc + it is more of a overriding navigation action anyway.
        relativeSubject.onNext(Direction.NEXT)
    }

    fun prevPage() {
        Log.info("prevPage")
        relativeSubject.onNext(Direction.PREV)
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
                pageEventSubject.onNext(PageEvent.Loaded(it))
            }
        }
    }

    private fun handleCacheHit(cached: Subpage) {
        Log.debug("cached ${cached.location.page}")
        historyAdd(cached.location)
        pageEventSubject.onNext(PageEvent.Loaded(cached))
    }

    private fun handle(received: TTVContent, ref: Location): PageEvent {
        val pages = received.pages.map { Page(it) }
        pages.forEach { cache[it.number] = CacheEntry(it) }
        val sub = pages.firstOrNull()?.getSubpage(ref.sub)

        if (sub != null && (history.isEmpty() || history.peek().page != sub.location.page)) {
            historyAdd(sub.location)
        }

        return when (sub) {
            null -> PageEvent.NotFound(ref)
            else -> PageEvent.Loaded(sub)
        }
    }

    private fun historyAdd(location: Location) {

        if (history.isEmpty() || history.peek() != location) {
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
}
