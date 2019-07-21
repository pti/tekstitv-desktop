package fi.reuna.tekstitv

import java.awt.Color
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

class Configuration(
        val baseUrl: String,
        val apiKey: String,
        val startPage: Int,
        val backgroundColor: Color,
        val autoRefreshInterval: Duration,
        val cacheExpires: Duration,
        val margin: Int,
        val fontFamily: String,
        val shortcutFontFamily: String,
        val shortcutFontSizer: Double,
        val shortcutBackground: Color,
        val shortcutForeground: Color,
        val pageNumberFontFamily: String,
        val pageNumberFontSizer: Double,
        val pageNumberColorActive: Color,
        val pageNumberColorInactive: Color,
        val shiftEmptyFirstColumn: Boolean,
        val linkFocusColor: Color,
        val mouseEnabled: Boolean
) {

    companion object {

        val instance: Configuration

        init {
            val path = System.getProperty("tekstitv.cfg")?.let { Paths.get(it) } ?: Paths.get(System.getProperty("user.home"), ".tekstitv", "configuration.properties")

            instance = Files.newBufferedReader(path).use { reader ->
                val p = Properties()
                p.load(reader)

                Configuration(
                        p.getProperty("baseUrl"),
                        p.getProperty("apiKey"),
                        p.getIntProperty("startPage", 100)!!,
                        p.getColorProperty("backgroundColor", Color.BLACK)!!,
                        p.getDurationProperty("autoRefreshInterval", ChronoUnit.SECONDS, Duration.ofSeconds(60)),
                        p.getDurationProperty("cacheExpires", ChronoUnit.SECONDS, Duration.ofSeconds(60)),
                        p.getIntProperty("margin", 10)!!,
                        p.getProperty("fontFamily") ?: "Fira Mono",
                        p.getProperty("shortcutFontFamily") ?: "Fira Sans",
                        p.getDoubleProperty("shortcutFontSizer", 0.28),
                        p.getColorProperty("shortcutBackground", Color(0x1f1f1f))!!,
                        p.getColorProperty("shortcutForeground", Color(0xf1f1f1))!!,
                        p.getProperty("pageNumberFontFamily") ?: "Fira Mono",
                        p.getDoubleProperty("pageNumberFontSizer", 0.65),
                        p.getColorProperty("pageNumberColorActive", Color(0xffffff))!!,
                        p.getColorProperty("pageNumberColorInactive", Color(0xc0c0c0))!!,
                        p.getIntProperty("shiftEmptyFirstColumn", 1) == 1,
                        p.getColorProperty("linkFocusColor", Color(0x30ffffff, true))!!,
                        p.getIntProperty("mouseEnabled", 1) == 1
                )
            }
        }
    }
}

fun Properties.getIntProperty(name: String, defaultValue: Int? = null, radix: Int = 10): Int? {
    return getProperty(name)?.toIntOrNull(radix) ?: defaultValue
}

private fun Properties.getColorProperty(name: String, defaultValue: Color? = null): Color? {
    return getIntProperty(name, null, radix = 16)?.let { Color(it, true) } ?: defaultValue
}

private fun Properties.getDurationProperty(name: String, unit: TemporalUnit, defaultValue: Duration): Duration {
    return Duration.of(getIntProperty(name, defaultValue.get(unit).toInt())!!.toLong(), unit)
}

private fun Properties.getDoubleProperty(name: String, defaultValue: Double): Double {
    return getProperty(name)?.toDouble() ?: defaultValue
}
