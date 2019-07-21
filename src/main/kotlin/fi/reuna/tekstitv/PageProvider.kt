package fi.reuna.tekstitv

import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import kotlin.concurrent.thread
import kotlin.concurrent.withLock

private data class CacheEntry(val page: Page, val added: Instant = Instant.now())

enum class Direction(val delta: Int) {
    NEXT(+1),
    PREV(-1);

    override fun toString(): String = name.toLowerCase()
}

interface PageEventListener {
    fun onPageEvent(event: PageEvent)
}

class PageProvider(private val listener: PageEventListener) {

    private val lock = ReentrantLock()
    private val history = Stack<Location>()
    private val cache = mutableMapOf<Int, CacheEntry>()
    private val jobs = PageJobConsumer()
    private val refreshDelayer by lazy { Debouncer() }

    val currentLocation: Location
        get() = lock.withLock { history.lastOrNull() ?: Location(100, 0) }


    fun stop() {
        refreshDelayer.stop()
        jobs.stop()
    }

    fun get(page: Int): Page? {
        return cache[page]?.page
    }

    fun set(location: Location, checkCache: Boolean = true, refresh: Boolean = false) {
        refreshDelayer.stop()
        val cached = if (checkCache) location.checkCache() else null

        if (cached != null) {
            jobs.clearAndIgnoreActive()
            handleCacheHit(cached)

        } else {
            jobs.add(PageJob(location, refresh = refresh))
        }
    }

    fun set(page: Int) {
        set(Location(page, 0))
    }

    fun refresh() {
        set(currentLocation, checkCache = false, refresh = true)
    }

    fun back() = lock.withLock {

        if (history.size > 1) {
            history.pop()
            set(currentLocation)
        }
    }

    fun togglePrevious() = lock.withLock {

        if (history.size > 1) {
            val current = history.pop()
            val previous = history.pop()
            history.push(current)
            set(previous)
        }
    }

    fun nextPage() {
        // User might press next/prev repeatedly. If one would just do the request directly, then the same page
        // request might get triggered. To avoid that and to allow the user to move multiple pages by pressing
        // next/prev rapidly, a queue is needed for the next/prev requests. Consume a request at a time.
        refreshDelayer.stop()
        jobs.add(PageJob(direction = Direction.NEXT))
    }

    fun prevPage() {
        refreshDelayer.stop()
        jobs.add(PageJob(direction = Direction.PREV))
    }

    fun nextSubpage() {
        setSubpage(direction = Direction.NEXT)
    }

    fun prevSubpage() {
        setSubpage(direction = Direction.PREV)
    }

    /**
     * Update the history so that for the current page the subpage number is the specified one.
     * Enables jumping to the 'correct' subpage (instead of always the first one) when navigating back in the history.
     */
    fun notifySubpageChanged(newSubpage: Int) {
        setSubpage(number = newSubpage, notify = false)
    }

    private fun setSubpage(number: Int? = null, direction: Direction? = null, notify: Boolean = true) {
        assert(number != null || direction != null)
        assert(number == null || number >= 0)
        jobs.clearAndIgnoreActive()
        var event: PageEvent? = null

        lock.withLock {
            val page = cache[currentLocation.page]?.page

            if (page != null) {
                val numSubs = page.subpages.size
                var newSubpage = number

                if (newSubpage == null) {
                    newSubpage = (currentLocation.sub + direction!!.delta) % numSubs
                    if (newSubpage < 0) newSubpage = numSubs - 1
                }

                // Instead of adding another instance of the current page to the history stack, replace page's current instance with updated subpage. Quicker to move backwards in history this way.
                currentLocation.withSub(newSubpage).fromCache()?.let {
                    history.pop()
                    history.push(it.location)
                    event = PageEvent.Loaded(it, cached = true)
                }
            }
        }

        if (notify) event?.let { notify(it) }
    }

    private fun handleCacheHit(cached: Subpage) {
        Log.debug("cached ${cached.location.page}")
        historyAdd(cached.location)
        notify(PageEvent.Loaded(cached, cached = true))
    }

    private fun historyAdd(location: Location) = lock.withLock {

        if (history.isEmpty() || history.peek().page != location.page) {
            history.push(location)
        }
    }

    private fun Location.fromCache(): Subpage? = lock.withLock {
        return cache[page]?.page?.getSubpage(sub)
    }

    private fun Location.checkCache(): Subpage? = lock.withLock {
        val cacheEntry = cache[page]

        if (cacheEntry != null) {
            // Always return the cached version immediately and automatically refresh after delay if necessary.
            // This way the app reacts to page selection (absolute or relative) immediately which is
            // especially nice when browsing back in the history and the connection isn't that fast.
            if (cacheEntry.added.since() >= Configuration.instance.cacheExpires) {
                refreshDelayer.start(Configuration.instance.autoRefreshDelay) {
                    if (currentLocation == this) {
                        Log.debug("delayed refresh of page ${this.page}")
                        refresh()
                    }
                }
            }
        }

        return cacheEntry?.page?.getSubpage(sub)
    }

    private fun Instant.since(): Duration {
        return Duration.between(this, Instant.now())
    }

    private fun Throwable.asPageEvent(location: Location, autoReload: Boolean): PageEvent {
        var type = ErrorType.OTHER

        if (this is PageNotFoundException || (this is HttpException && status == 404)) {
            type = ErrorType.NOT_FOUND
        }

        return PageEvent.Failed(type, this, location, autoReload)
    }

    private fun notify(event: PageEvent) {

        if (SwingUtilities.isEventDispatchThread()) {
            listener.onPageEvent(event)
        } else {
            SwingUtilities.invokeLater { listener.onPageEvent(event) }
        }
    }

    private inner class PageJobConsumer {

        private val ttv = TTVService()
        private val jobs = ArrayBlockingQueue<PageJob>(10)
        private val running = AtomicBoolean(false)
        private val stopped = AtomicBoolean(false)
        private val reqId = AtomicInteger(1)
        private var ignoreId = AtomicInteger(0)

        fun stop() {
            stopped.set(true)
            jobs.add(PageJob()) // Wake up the consumer thread in case it is waiting for a job.
        }

        fun clearAndIgnoreActive() {
            ignoreId.set(reqId.get())
            jobs.clear()
        }

        fun add(job: PageJob) {

            if (stopped.get()) {
                return
            }

            if (!running.get()) {
                // Consumer thread is started here in case an exception is thrown
                thread { consume() }
            }

            if (job.direction == null) {
                // Other requests (most likely relative ones) aren't relevant after setting an absolute position,
                // so empty the queue.
                clearAndIgnoreActive()
            }

            // Fail instead of blocking (the UI) if queue is full. 10 next/prev jobs should be enough for anybody.
            // And the limitation doesn't actually mean anything - user can keep on pressing next/prev key and
            // the jobs will eventually get handled.
            jobs.add(job)
        }

        private fun consume() {

            try {
                running.set(true)

                while (!stopped.get()) {
                    val job = jobs.take()

                    if (job.location == null && job.direction == null) {
                        break
                    }

                    val location = job.location ?: currentLocation

                    try {
                        reqId.incrementAndGet()

                        val body = if (job.direction != null) {
                            // TODO some kind of linked list to cache so that we can check for other than just the immediate page
                            val hit = location.move(job.direction).checkCache()

                            if (hit != null) {
                                handleCacheHit(hit)
                                continue
                            }

                            // If the relativeTo points to a non-existing page, then nextPage/prevPage requests always fail.
                            // If that happens, simply try to request the current page +- 1. This is useful in case
                            // the user has entered an invalid page and then attempts to move to nextPage/prevPage page.
                            exec(location.page, job.direction, notFoundLocation = location.move(job.direction))

                        } else {
                            exec(job.location!!.page)
                        }

                        val pages = body.pages.map { Page(it) }
                        val old = cache[location.page]?.page

                        lock.withLock {
                            pages.forEach { cache[it.number] = CacheEntry(it) }
                        }

                        val sub = pages.firstOrNull()?.subpages?.firstOrNull()

                        if (ignoreId.get() == reqId.get()) {
                            Log.debug("ignore response to req #${reqId.get()} page=${location.page} d=${job.direction}")

                        } else if (job.refresh && sub?.location != currentLocation) {
                            Log.debug("refresh: current location changed - ignore")

                        } else {

                            if (!job.refresh && sub != null) {
                                historyAdd(sub.location)
                            }

                            val noChange = job.refresh && old != null && sub?.timestamp != null && sub.timestamp == old.getSubpage(location.sub)?.timestamp

                            val event = when (sub) {
                                null -> PageEvent.Failed(ErrorType.NOT_FOUND, null, location, job.refresh)
                                else -> PageEvent.Loaded(sub, noChange = noChange)
                            }

                            notify(event)
                        }

                    } catch (pnfe: PageNotFoundException) {
                        val loc = Location(pnfe.page, 0)
                        // loc can be different than location in case the 'notFoundLocation' was used.
                        Log.error("page ${pnfe.page} not found")
                        historyAdd(loc)
                        notify(pnfe.asPageEvent(loc, job.refresh))

                    } catch (t: Throwable) {
                        Log.error("page request failed", t)
                        historyAdd(location)
                        notify(t.asPageEvent(location, job.refresh))
                    }
                }

            } catch (t: Throwable) {
                Log.error("error processing page jobs queue", t)

            } finally {
                running.set(false)
            }
        }

        private fun exec(page: Int, rel: Direction? = null, notFoundLocation: Location? = null): TTVContent {

            return try {
                ttv.get(page, rel)

            } catch (he: HttpException) {

                if (he.status == 404) {

                    if (notFoundLocation != null) {
                        exec(notFoundLocation.page)
                    } else {
                        throw PageNotFoundException(page)
                    }

                } else {
                    throw he
                }
            }
        }
    }
}

private data class PageJob(val location: Location? = null, val direction: Direction? = null, val refresh: Boolean = false)
