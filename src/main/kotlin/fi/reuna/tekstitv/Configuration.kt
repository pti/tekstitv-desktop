package fi.reuna.tekstitv

import java.awt.Color
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit
import java.util.*

fun propertiesPath(name: String): Path {
    return Paths.get(System.getProperty("user.home"), ".tekstitv", name)
}

class Configuration(
        val baseUrl: String,
        val appId: String,
        val appKey: String,
        val startPage: Int,
        val backgroundColor: Color,
        val autoRefreshInterval: Duration,
        val autoRefreshDelay: Duration,
        val cacheRefreshAfter: Duration,
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
        val mouseEnabled: Boolean,
        val navigationHistoryEnabled: Boolean,
        val loadingMessageDelay: Duration,
        val applyDesktopHints: Boolean
) {

    companion object {

        val instance: Configuration

        init {
            val path = System.getProperty("tekstitv.cfg")?.let { Paths.get(it) } ?: propertiesPath("configuration.properties")

            instance = Files.newBufferedReader(path).use { reader ->
                val p = Properties()
                p.load(reader)

                Configuration(
                        p.getProperty("baseUrl"),
                        p.getProperty("appId"),
                        p.getProperty("appKey"),
                        p.getIntProperty("startPage", 100)!!,
                        p.getColorProperty("backgroundColor", Color.BLACK, hasAlpha = false)!!,
                        p.getDurationProperty("autoRefreshInterval", ChronoUnit.SECONDS, Duration.ofSeconds(60)),
                        p.getDurationProperty("autoRefreshDelay", ChronoUnit.MILLIS, Duration.ofMillis(750)),
                        p.getDurationProperty("cacheRefreshAfter", ChronoUnit.SECONDS, Duration.ofSeconds(60)),
                        p.getDurationProperty("cacheExpires", ChronoUnit.MINUTES, Duration.ofMinutes(60)),
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
                        p.getIntProperty("mouseEnabled", 1) == 1,
                        p.getIntProperty("navigationHistoryEnabled", 1) == 1,
                        p.getDurationProperty("loadingMessageDelay", ChronoUnit.MILLIS, Duration.ofMillis(500)),
                        p.getIntProperty("applyDesktopHints", 1) == 1
                )
            }
        }
    }
}

fun Properties.getIntProperty(name: String, defaultValue: Int? = null, radix: Int = 10): Int? {
    return getProperty(name)?.toIntOrNull(radix) ?: defaultValue
}

private fun Properties.getColorProperty(name: String, defaultValue: Color? = null, hasAlpha: Boolean = true): Color? {
    return getIntProperty(name, null, radix = 16)?.let { Color(it, hasAlpha) } ?: defaultValue
}

private fun Properties.getDurationProperty(name: String, unit: TemporalUnit, defaultValue: Duration): Duration {
    val value = getIntProperty(name)?.toLong()
    return if (value != null) Duration.of(value, unit) else defaultValue
}

private fun Properties.getDoubleProperty(name: String, defaultValue: Double): Double {
    return getProperty(name)?.toDouble() ?: defaultValue
}
