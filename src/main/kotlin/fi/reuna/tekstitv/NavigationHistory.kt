package fi.reuna.tekstitv

import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonArray
import com.eclipsesource.json.JsonObject
import java.io.FileOutputStream
import java.nio.file.Paths
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.atomic.AtomicBoolean

class NavigationHistory private constructor() {

    private var hitsByPage = mutableMapOf<Int, MutableList<PageHit>>()

    private val file = System.getProperty("tekstitv.history")?.let { Paths.get(it) } ?: propertiesPath("history.json")

    private val saver = Debouncer()
    private var changed = AtomicBoolean(false)
    private var saving = AtomicBoolean(false)
    private val enabled = Configuration.instance.navigationHistoryEnabled

    init {

        if (enabled) {
            load()

            Runtime.getRuntime().addShutdownHook(Thread {
                saver.destroy()

                if (changed.get() && !saving.get()) {
                    save()
                }
            })
        }
    }

    fun close() {
        saver.destroy()
    }

    fun add(source: Int, destination: Int) {
        if (!enabled) return

        var hits = hitsByPage[source]

        if (hits == null) {
            hits = mutableListOf()
            hitsByPage[source] = hits
        }

        hits.add(PageHit(source, destination))
        changed.set(true)

        saver.start(Duration.ofMinutes(1)) { save() }
    }

    /**
     * Return pages most visited from page [source].
     */
    fun topHits(source: Int, count: Int, minCount: Int? = 3, minOccurred: Instant? = Instant.now().minus(7, ChronoUnit.DAYS), ignore: Array<Int>? = null): List<Int> {
        if (!enabled || count <= 0) return emptyList()
        val all = hitsByPage[source] ?: emptyList<PageHit>()
        return all.asSequence()
                .filter { ignore == null || !ignore.contains(it.destination) }
                .filter { minOccurred == null || it.occurred.isAfter(minOccurred) }
                .groupBy { it.destination }
                .map { it.value }
                .filter { minCount == null || it.size >= minCount }
                .sortedByDescending { it.size }
                .take(count)
                .map { it.first().destination }
                .toList()
    }

    private fun load() {

        try {
            Log.debug("begin")

            val file = this.file.toFile()
            if (!file.exists()) return

            val data = file.bufferedReader().use { HistoryData(Json.parse(it).asObject()) }
            Log.debug("got ${data.hits.size} history entries")
            val byPage = data.hits.groupBy { it.source }
            hitsByPage = byPage.mapValues { it.value.toMutableList() }.toMutableMap()
            Log.debug("data initialized")

        } catch (e: Exception) {
            Log.error("error loading navigation history", e)
        }
    }

    private fun save() {

        try {
            saving.set(true)

            FileOutputStream(file.toFile(), false).bufferedWriter().use {
                Log.debug("begin")
                val all = hitsByPage.flatMap { it.value }
                Log.debug("save ${all.size} hits")
                HistoryData(all).toJson().writeTo(it)
                changed.set(false)
                Log.debug("done")
            }

        } catch (e: Exception) {
            Log.error("error saving navigation history", e)

        } finally {
            saving.set(false)
        }
    }

    companion object {

        val instance = NavigationHistory()
    }
}

data class PageHit(val source: Int, val destination: Int, val occurred: Instant = Instant.now()) {

    constructor(json: JsonObject): this(
            json.get("source").asInt(),
            json.get("destination").asInt(),
            json.get("occurred").asTimestamp()
    )

    fun toJson(): JsonObject {
        return JsonObject()
                .add("source", source)
                .add("destination", destination)
                .add("occurred", occurred)
    }
}

data class HistoryData(val hits: List<PageHit>) {

    constructor(json: JsonObject): this(
            json.get("hits").asArray().map { PageHit(it.asObject()) }
    )

    fun toJson(): JsonObject {
        val array = JsonArray()
        hits.forEach { array.add(it.toJson()) }
        return JsonObject().add("hits", array)
    }
}
