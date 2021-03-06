package fi.reuna.tekstitv

import java.time.Duration
import java.time.Instant
import java.util.*
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

    private val ttv = TeletextService()
    private val lock = ReentrantLock()
    private val history = Stack<Location>()
    private val cache = mutableMapOf<Int, CacheEntry>()
    private val jobs = PageJobConsumer()
    private val refreshDelayer by lazy { Debouncer() }

    val currentLocation: Location
        get() = lock.withLock { history.lastOrNull() ?: Location(100, 0) }

    val currentPage: Page?
        get() = lock.withLock { history.lastOrNull()?.let { cache[it.page]?.page } }


    fun destroy() {
        refreshDelayer.destroy()
        jobs.stop()
    }

    fun get(page: Int): Page? {
        return cache[page]?.page
    }

    fun set(location: Location, checkCache: Boolean = true, refresh: Boolean = false) {
        refreshDelayer.stop()
        val cached = if (checkCache) location.checkCache()?.getSubpage(location.sub) else null

        if (cached != null) {
            jobs.clearAndIgnoreActive()
            handleCacheHit(cached)

        } else {
            jobs.add(PageJob.Absolute(location, refresh))
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
        jobs.add(PageJob.Relative(Direction.NEXT))
    }

    fun prevPage() {
        refreshDelayer.stop()
        jobs.add(PageJob.Relative(Direction.PREV))
    }

    fun nextSubpage() {
        setSubpage(direction = Direction.NEXT)
    }

    fun prevSubpage() {
        setSubpage(direction = Direction.PREV)
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

                // Instead of adding another instance of the current page to the history stack,
                // replace page's current instance with updated subpage. Quicker to move backwards in history this way.
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

    private fun Location.checkCache(): Page? = lock.withLock {
        val cacheEntry = cache[page]

        if (cacheEntry != null) {
            val cfg = Configuration.instance
            val entryAge = cacheEntry.added.since()

            // Two levels of cache expiration are used:

            // First one (cacheExpires) is used to avoid displaying 'really' old pages to the user.
            // For example one could've left the app running in the background and then it is better to displaying
            // a blank page while loading a requested page than to display a page that likely contains old information.
            if (entryAge >= cfg.cacheExpires) {
                Log.debug("cache entry too old - remove")
                cache.remove(page)
                return null
            }

            // The second (cacheRefreshAfter) is used to return the cached page if it is quite fresh,
            // but old enough to qualify for refreshing. This way the app reacts to page selection (absolute or relative)
            // immediately which is especially nice when browsing back in the history and the connection isn't that fast.
            // autoRefreshDelay is used to avoid a page load request in case the user quickly moves to another page.
            if (entryAge >= cfg.cacheRefreshAfter) {
                refreshDelayer.start(cfg.autoRefreshDelay) {
                    if (currentLocation == this) {
                        Log.debug("delayed refresh of page ${this.page}")
                        refresh()
                    }
                }
            }
        }

        return cacheEntry?.page
    }

    private fun Throwable.asPageEvent(req: PageRequest): PageEvent {
        var type = ErrorType.OTHER

        if (this is HttpException && status == 404) {
            type = ErrorType.NOT_FOUND
        }

        return PageEvent.Failed(type, this, req)
    }

    private fun notify(event: PageEvent) {

        if (SwingUtilities.isEventDispatchThread()) {
            listener.onPageEvent(event)
        } else {
            SwingUtilities.invokeLater { listener.onPageEvent(event) }
        }
    }

    private inner class PageJobConsumer {

        private val jobs = mutableListOf<PageJob>()
        private val jobsLock = ReentrantLock()
        private val notEmpty = jobsLock.newCondition()
        private var currentJob: PageJob? = null
        private val running = AtomicBoolean(false)
        private val stopped = AtomicBoolean(false)
        private val reqId = AtomicInteger(1)
        private var ignoreId = AtomicInteger(0)

        fun stop() {
            clearAndIgnoreActive()
            stopped.set(true)
            jobsLock.withLock { notEmpty.signal() }
        }

        fun clearAndIgnoreActive() {
            if (stopped.get()) return
            ignoreId.set(reqId.get())
            jobsLock.withLock { jobs.clear() }
        }

        fun add(job: PageJob) {

            if (stopped.get()) {
                return
            }

            val prevJob = currentOrNextJob

            if (job is PageJob.Absolute && prevJob is PageJob.Absolute && job.location == prevJob.location) {
                // Avoid doing the same absolute request back-to-back. This can happen e.g. when refreshing too
                // frequently (and the request takes a while to complete). Cannot just check for job equality as having
                // multiple consecutive (location=null, direction=next/prev) jobs is ok.
                Log.debug("duplicate job - ignore: $job")
                return
            }

            if (job is PageJob.Absolute) {
                // Other requests (most likely relative ones) aren't relevant after setting an absolute position,
                // so empty the queue.
                clearAndIgnoreActive()
            }

            // Fail instead of blocking (the UI) if queue is full. And the limitation doesn't actually mean anything -
            // user can keep on pressing next/prev key and the jobs will eventually get handled.
            addJobToQueue(job)

            if (!running.get()) {
                running.set(true)
                thread { consume() }
            }
        }

        private fun consume() {

            try {

                while (!stopped.get()) {
                    val job = getJobForConsumption() ?: break

                    val location = when (job) {
                        is PageJob.Absolute -> job.location
                        is PageJob.Relative -> currentPage?.relativePageNumber(job.direction)?.asLocation()
                                ?: currentLocation.move(job.direction)
                    }

                    val req = when (job) {
                        is PageJob.Absolute -> PageRequest(location, refresh = job.refresh)
                        is PageJob.Relative -> PageRequest(location, direction = job.direction)
                    }

                    try {
                        reqId.incrementAndGet()

                        if (req.direction != null) {
                            // In the next/prev page case cache isn't checked immediately in next/prevPage, but later
                            // here when processing the job queue so that requests gets processed in order.
                            // At this point req's location already takes the direction into account.
                            val hit = req.location.checkCache()?.getSubpage(0)

                            if (hit != null) {
                                handleCacheHit(hit)
                                continue
                            }
                        }

                        val resp = exec(req)
                        val page = Page(resp)
                        Log.debug("got: number=${page.number}, next=${page.nextPage}, prev=${page.prevPage}")
                        val old = cache[page.number]?.page

                        lock.withLock {
                            cache[page.number] = CacheEntry(page)
                        }

                        val sub = page.getSubpage(req.location.sub) ?: page.subpages.firstOrNull()

                        if (ignoreId.get() == reqId.get()) {
                            Log.debug("ignore response to req #${reqId.get()} page=${req.location.page} d=${req.direction}")
                            notify(PageEvent.Ignored(req))

                        } else if (req.refresh && sub?.location != currentLocation) {
                            Log.debug("refresh: current location changed - ignore")
                            notify(PageEvent.Ignored(req))

                        } else {

                            if (!req.refresh && sub != null) {
                                historyAdd(sub.location)
                            }

                            val noChange = req.refresh && old != null && old.subpages == page.subpages

                            val event = when (sub) {
                                null -> PageEvent.Failed(ErrorType.NOT_FOUND, null, req)
                                else -> PageEvent.Loaded(sub, noChange = noChange)
                            }

                            notify(event)
                        }

                    } catch (pre: PageRequestException) {
                        handleRequestFailure(pre.request, pre)

                    } catch (t: Throwable) {
                        handleRequestFailure(req, t)

                    } finally {
                        jobConsumed()
                    }
                }

            } catch (t: Throwable) {
                Log.error("error processing page jobs queue", t)

            } finally {
                running.set(false)
            }
        }

        private fun handleRequestFailure(req: PageRequest, t: Throwable) {
            Log.error("page request failed", t)
            historyAdd(req.location)
            notify(t.asPageEvent(req))
        }

        private fun exec(req: PageRequest): TeletextPage {

            return try {
                notify(PageEvent.Loading(req))
                ttv.get(req.location.page)

            } catch (he: HttpException) {

                if (he.status == 404) {
                    throw PageRequestException(he.status, he.message, req)

                } else {
                    throw he
                }
            }
        }

        private fun addJobToQueue(job: PageJob) = jobsLock.withLock {
            jobs.add(job)
            notEmpty.signal()
        }

        private fun getJobForConsumption(): PageJob? = jobsLock.withLock {
            while (jobs.isEmpty() && !stopped.get()) notEmpty.await()

            return if (jobs.isEmpty()) {
                null

            } else {
                val job = jobs.removeAt(0)
                currentJob = job
                job
            }
        }

        private fun jobConsumed() = jobsLock.withLock {
            currentJob = null
        }

        private val currentOrNextJob: PageJob? = jobsLock.withLock {
            currentJob ?: jobs.firstOrNull()
        }
    }
}

fun Instant.since(): Duration = Duration.between(this, Instant.now())

private sealed class PageJob {
    data class Absolute(val location: Location, val refresh: Boolean) : PageJob()
    data class Relative(val direction: Direction) : PageJob()
}
