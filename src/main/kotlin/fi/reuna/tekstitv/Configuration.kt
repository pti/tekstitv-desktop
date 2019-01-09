package fi.reuna.tekstitv

import com.squareup.moshi.Moshi
import org.hjson.JsonValue
import java.nio.file.Files
import java.nio.file.Paths

object ConfigurationProvider {

    val cfg: Configuration

    init {
        val path = Paths.get(System.getProperty("user.home"), ".tekstitv", "configuration.hjson")

        cfg = Files.newBufferedReader(path).use { reader ->
            val input = JsonValue.readHjson(reader).toString()
            val moshi = Moshi.Builder().build()
            val cfg = moshi.adapter(Configuration::class.java).fromJson(input)
            cfg ?: throw IllegalStateException("Failed to read configuration from ${path.toAbsolutePath()}")
        }
    }
}

class Configuration(
        val baseUrl: String,
        val apiKey: String
)
