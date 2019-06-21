package fi.reuna.tekstitv

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import fi.reuna.tekstitv.adapters.InstantAdapter
import okio.Okio
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.schedule

class NavigationHistory {

    private var hitsByPage = mutableMapOf<Int, MutableList<PageHit>>()

    private val file: Path = System.getProperty("tekstitv.history")
            ?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"), ".tekstitv", "history.json")

    private val saveTimer = Timer()
    private var saveTask: TimerTask? = null
    private var changed = AtomicBoolean(false)
    private var saving = AtomicBoolean(false)

    init {

        Runtime.getRuntime().addShutdownHook(Thread {
            saveTimer.cancel()

            if (changed.get() && !saving.get()) {
                save()
            }
        })
    }

    fun close() {
        saveTimer.cancel()
    }

    fun add(source: Int, destination: Int) {
        var hits = hitsByPage[source]

        if (hits == null) {
            hits = mutableListOf()
            hitsByPage[source] = hits
        }

        hits.add(PageHit(source, destination))
        changed.set(true)

        saveTask?.cancel()
        saveTask = saveTimer.schedule(60000) { save() }
    }

    /**
     * Return pages most visited from page [source].
     */
    fun topHits(source: Int, count: Int, minCount: Int? = 3, minOccurred: Instant? = Instant.now().minus(7, ChronoUnit.DAYS)): List<Int> {
        val all = hitsByPage[source] ?: emptyList<PageHit>()
        return all.asSequence()
                .filter { minOccurred == null || it.occurred.isAfter(minOccurred) }
                .groupBy { it.destination }
                .map { it.value }
                .filter { minCount == null || it.size >= minCount }
                .sortedByDescending { it.size }
                .take(count)
                .map { it.first().destination }
                .toList()
    }

    fun load() {

        try {
            if (!file.toFile().exists()) return

            Okio.source(file, StandardOpenOption.READ).use { source ->
                val data = adapter.fromJson(Okio.buffer(source))
                val byPage = data?.hits?.groupBy { it.source }

                if (byPage == null) {
                    hitsByPage.clear()
                } else {
                    hitsByPage = byPage.mapValues { it.value.toMutableList() }.toMutableMap()
                }
            }

        } catch (e: Exception) {
            Log.error("error loading navigation history", e)
        }
    }

    fun save() {

        try {
            saving.set(true)

            Okio.sink(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING).use { sink ->
                Log.debug("begin")
                val all = hitsByPage.flatMap { it.value }
                Log.debug("save ${all.size} hits")
                val data = HistoryData(all)

                Okio.buffer(sink).use { buf ->
                    adapter.toJson(buf, data)
                    buf.flush()
                }

                changed.set(false)
                Log.debug("done")
            }

        } catch (e: Exception) {
            Log.error("error saving navigation history", e)

        } finally {
            saving.set(false)
        }
    }

    private val moshi: Moshi by lazy {
        Moshi.Builder()
                .add(Instant::class.java, InstantAdapter())
                .build()
    }

    private val adapter: JsonAdapter<HistoryData> by lazy {
        moshi.adapter(HistoryData::class.java)
    }

    companion object {

        val instance = NavigationHistory()
    }
}

@JsonClass(generateAdapter = true)
data class PageHit(val source: Int, val destination: Int, val occurred: Instant = Instant.now())

@JsonClass(generateAdapter = true)
data class HistoryData(val hits: List<PageHit>)
