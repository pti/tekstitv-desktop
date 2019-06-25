package fi.reuna.tekstitv

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.adapters.Rfc3339DateJsonAdapter
import java.io.FileNotFoundException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.file.Paths
import java.util.*
import javax.xml.ws.http.HTTPException

class TTVService {

    private val jsonAdapter: JsonAdapter<TTVContent>
    private val baseUrl: String

    init {
        val cfg = ConfigurationProvider.cfg
        baseUrl = "${cfg.baseUrl}${if (cfg.baseUrl.endsWith("/")) "" else "/"}ttvcontent?a=${cfg.apiKey}&c=true"

        jsonAdapter = Moshi.Builder()
                .add(Date::class.java, Rfc3339DateJsonAdapter())
                .build()
                .adapter(TTVContent::class.java)
    }

    fun get(page : Int, rel: Direction? = null): TTVContent {
        var url = "$baseUrl&p=$page"
        if (rel != null) url += "&s=$rel"
        Log.debug("send $url")

        with(URL(url).openConnection() as HttpURLConnection) {
            val status = responseCode
            Log.debug("recv $url [$status]")

            if (status in 200..299) {
                val body = inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
                return jsonAdapter.fromJson(body) ?: throw IOException("Could not parse response")

            } else {
                throw HTTPException(status)
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class TTVContent(val status: Int,
                 val message: String?,
                 val timestamp: Date,
                 val pagesCount: Int,
                 val version: String,
                 val pages: List<TTVPage>)

@JsonClass(generateAdapter = true)
data class TTVPage(val number: Int, val subpages: List<TTVSubpage>)

@JsonClass(generateAdapter = true)
data class TTVSubpage(val number: Int, val timestamp: Date, val content: String)
