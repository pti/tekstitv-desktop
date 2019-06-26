package fi.reuna.tekstitv

import com.eclipsesource.json.Json
import com.eclipsesource.json.JsonObject
import com.eclipsesource.json.JsonValue
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.format.DateTimeFormatter
import javax.xml.ws.http.HTTPException

class TTVService {

    private val baseUrl: String

    init {
        val cfg = ConfigurationProvider.cfg
        baseUrl = "${cfg.baseUrl}${if (cfg.baseUrl.endsWith("/")) "" else "/"}ttvcontent/?a=${cfg.apiKey}&c=true"
    }

    fun get(page : Int, rel: Direction? = null): TTVContent {
        var url = "$baseUrl&p=$page"
        if (rel != null) url += "&s=$rel"
        Log.debug("send $url")

        with(URL(url).openConnection() as HttpURLConnection) {
            val t0 = System.nanoTime()
            val status = responseCode
            val t1 = System.nanoTime()
            Log.debug("recv $url [$status] (${(t1 - t0) / 1000000L}ms)")

            if (status in 200..299) {
                val json = inputStream.bufferedReader().use { Json.parse(it).asObject() }
                return TTVContent(json)

            } else {
                throw HTTPException(status)
            }
        }
    }
}

data class TTVContent(val status: Int?,
                 val message: String?,
                 val timestamp: Instant?,
                 val pagesCount: Int?,
                 val version: String?,
                 val pages: List<TTVPage>) {

    constructor(json: JsonObject) : this(
            json.get("status")?.asInt(),
            json.get("message")?.asString(),
            json.get("timestamp")?.asTimestamp(),
            json.get("pagesCount")?.asInt(),
            json.get("version")?.asString(),
            json.get("pages")?.asArray()?.map { TTVPage(it.asObject()) } ?: emptyList()
    )
}

data class TTVPage(val number: Int, val subpages: List<TTVSubpage>) {

    constructor(json: JsonObject) : this(
            json.get("number").asInt(),
            json.get("subpages").asArray().map { TTVSubpage(it.asObject()) }
    )
}

data class TTVSubpage(val number: Int, val timestamp: Instant?, val content: String) {

    constructor(json: JsonObject) : this(
            json.get("number").asInt(),
            json.get("timestamp")?.asTimestamp(),
            json.get("content").asString()
    )
}

fun JsonValue.asTimestamp(): Instant {
    return DateTimeFormatter.ISO_DATE_TIME.parse(asString(), Instant::from)
}

fun JsonObject.add(name: String, value: Instant): JsonObject {
    return add(name, DateTimeFormatter.ISO_INSTANT.format(value))
}
