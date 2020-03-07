package fi.reuna.tekstitv

import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonObject
import com.eclipsesource.json.JsonValue
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class TTVService {

    private val baseUrl: String
    private val userAgent: String
    private val appQueryParams: String

    init {
        val cfg = Configuration.instance
        baseUrl = cfg.baseUrl.removeSuffix("/")
        appQueryParams = "app_id=${cfg.appId}&app_key=${cfg.appKey}"

        val pkg = javaClass.`package`
        val osInfo = "${System.getProperty("os.name")}; ${System.getProperty("os.arch")}; ${System.getProperty("os.version")}"
        userAgent = "${pkg.implementationTitle}/${pkg.implementationVersion} ($osInfo)"
    }

    fun get(page : Int): TeletextPage {
        val url = "$baseUrl/v1/teletext/pages/${page}.json?${appQueryParams}"
        Log.debug("send $url")

        with(URL(url).openConnection() as HttpURLConnection) {
            setRequestProperty("User-Agent", userAgent)
            val t0 = System.nanoTime()
            val status = responseCode
            val t1 = System.nanoTime()
            Log.debug("recv $url [$status] (${(t1 - t0) / 1000000L}ms)")

            if (status in 200..299) {
                val json = inputStream.bufferedReader().use { Json.parse(it).asObject().getObject("teletext")!!.getObject("page")!! }
                return TeletextPage(json)

            } else {
                throw HttpException(status, responseMessage)
            }
        }
    }
}

data class TeletextPage(
       val number: Int,
       val nextPage: Int?,
       val prevPage: Int?,
       val timestamp: Instant?,
       val subpages: List<TeletextSubpage>
) {
    constructor(json: JsonObject) : this(
            json.getStringAsInt("number")!!,
            json.getStringAsInt("nextpg"),
            json.getStringAsInt("prevpg"),
            json.get("time")?.asTimestamp(),
            json.get("subpage")
                    ?.asArray()
                    ?.mapNotNull {
                        it.asObject()
                                ?.get("content")
                                ?.asArray()
                                ?.values()
                                ?.map { v -> v.asObject() }
                                ?.first { v -> v.getString("type", "und") == "all" }
                    }
                    ?.map { TeletextSubpage(it) }
                    ?: emptyList()
    )
}

data class TeletextSubpage(val lines: List<String>) {

    constructor(json: JsonObject) : this(
            json.get("line")
                    .asArray()
                    .map { it.asObject().getString("Text", "") }
    )
}

fun JsonValue.asTimestamp(): Instant {
    return DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.of("Europe/Helsinki")).parse(asString(), Instant::from)
}

fun JsonObject.add(name: String, value: Instant): JsonObject {
    return add(name, DateTimeFormatter.ISO_INSTANT.format(value))
}

fun JsonObject.getObject(name: String): JsonObject? {
    return get(name)?.asObject()
}

fun JsonObject.getStringAsInt(name: String): Int? {
    return get(name)?.asString()?.toInt()
}

open class HttpException(val status: Int, message: String?): Exception(message)
