package fi.reuna.tekstitv

import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.ReentrantLock
import javax.swing.SwingUtilities
import javax.xml.ws.http.HTTPException
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

    val currentLocation: Location
        get() = lock.withLock { history.lastOrNull() ?: Location(100, 0) }

    val currentPage: Subpage?
        get() = currentLocation.fromCache()


    fun stop() {
        jobs.stop()
    }

    fun set(location: Location, checkCache: Boolean = true, autoReload: Boolean = false) {
        val cached = if (checkCache) location.checkCache() else null

        if (cached != null) {
            jobs.clearAndIgnoreActive()
            handleCacheHit(cached)

        } else {
            jobs.add(PageJob(location, autoReload = autoReload))
        }
    }

    fun set(page: Int) {
        set(Location(page, 0))
    }

    fun reload(autoReload: Boolean = false) {
        set(currentLocation, checkCache = false, autoReload = autoReload)
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
        jobs.add(PageJob(direction = Direction.NEXT))
    }

    fun prevPage() {
        jobs.add(PageJob(direction = Direction.PREV))
    }

    fun nextSubpage() {
        setSubpage(Direction.NEXT)
    }

    fun prevSubpage() {
        setSubpage(Direction.PREV)
    }

    fun setSubpage(direction: Direction) {
        jobs.clearAndIgnoreActive()
        var event: PageEvent? = null

        lock.withLock {
            val page = cache[currentLocation.page]?.page

            if (page != null) {
                val numSubs = page.subpages.size
                var newSubpage = (currentLocation.sub + direction.delta) % numSubs
                if (newSubpage < 0) newSubpage = numSubs - 1

                // Instead of adding another instance of the current page to the history stack, replace page's current instance with updated subpage. Quicker to move backwards in history this way.
                currentLocation.withSub(newSubpage).fromCache()?.let {
                    history.pop()
                    history.push(it.location)
                    event = PageEvent.Loaded(it, cached = true)
                }
            }
        }

        event?.let { notify(it) }
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

        if (this is HTTPException && statusCode == 404) {
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

                        lock.withLock {
                            pages.forEach { cache[it.number] = CacheEntry(it) }
                        }

                        val sub = pages.firstOrNull()?.getSubpage(location.sub)

                        if (ignoreId.get() == reqId.get()) {
                            Log.debug("ignore response to req #${reqId.get()} page=${location.page} d=${job.direction}")

                        } else {

                            if (!job.autoReload && sub != null) {
                                historyAdd(sub.location)
                            }

                            val event = when (sub) {
                                null -> PageEvent.Failed(ErrorType.NOT_FOUND, null, location, job.autoReload)
                                else -> PageEvent.Loaded(sub)
                            }

                            // Ignore the auto reload response if current location has changed.
                            if (!job.autoReload || event !is PageEvent.Loaded || event.subpage.location == currentLocation) {
                                notify(event)
                            }
                        }

                    } catch (t: Throwable) {
                        Log.error("page request failed", t)
                        historyAdd(location)
                        notify(t.asPageEvent(location, job.autoReload))
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

            } catch (he: HTTPException) {

                if (he.statusCode == 404 && notFoundLocation != null) {
                    exec(notFoundLocation.page)
                } else {
                    throw he
                }
            }
        }
    }
}

private data class PageJob(val location: Location? = null, val direction: Direction? = null, val autoReload: Boolean = false)
