package fi.reuna.tekstitv

import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import fi.reuna.tekstitv.adapters.ColorAdapter
import fi.reuna.tekstitv.adapters.DurationAdapter
import org.hjson.JsonValue
import java.awt.Color
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration

object ConfigurationProvider {

    val cfg: Configuration

    init {
        val path = System.getProperty("tekstitv.cfg")?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"), ".tekstitv", "configuration.hjson")

        cfg = Files.newBufferedReader(path).use { reader ->
            val input = JsonValue.readHjson(reader).toString()
            val moshi = Moshi.Builder()
                    .add(Color::class.java, ColorAdapter())
                    .add(Duration::class.java, DurationAdapter())
                    .build()
            val cfg = moshi.adapter(Configuration::class.java).fromJson(input)
            cfg ?: throw IllegalStateException("Failed to read configuration from ${path.toAbsolutePath()}")
        }
    }
}

@JsonClass(generateAdapter = true)
data class Configuration(
        val baseUrl: String,
        val apiKey: String,
        val startPage: Int = 100,
        val backgroundColor: Color = Color.BLACK,
        val autoRefreshInterval: Duration = Duration.ofSeconds(60),
        val margin: Int = 10,
        val fontFamily: String = "Fira Mono",
        val shortcutFontFamily: String = "Fira Sans",
        val shortcutFontSizer: Double = 0.34,
        val shortcutBackground: Color = Color(0x1f1f1f),
        val shortcutForeground: Color = Color(0xf1f1f1),
        val pageNumberFontFamily: String = "Fira Mono",
        val pageNumberFontSizer: Double = 0.65,
        val pageNumberColorActive: Color = Color(0xffffff),
        val pageNumberColorInactive: Color = Color(0xc0c0c0)
)
