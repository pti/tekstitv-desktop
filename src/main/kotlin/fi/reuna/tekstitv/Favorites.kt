package fi.reuna.tekstitv

import java.nio.file.Paths

class Favorites {

    /**
     * Array index represents a page number. For each page there are either no favorites (null element) or
     * 1-N favorite pages (to shown in the shortcuts when on that specific page).
     */
    private val byPage = Array<IntArray?>(1000) { null }

    fun getFavorites(page: Int): IntArray? = byPage[page]

    init {
        val path = System.getProperty("tekstitv.favorites")?.let { Paths.get(it) }
                ?: Paths.get(System.getProperty("user.home"), ".tekstitv", "favorites.properties")

        path.toFile().takeIf { it.exists() }?.bufferedReader()?.use { reader ->
            val pat = Regex("""^\s*(\*|[1-8]\d{2}|[1-8]\d{2}-[1-8]\d{2})\s*:\s*([\d,]+)\s*""")

            reader.readLines().forEach { line ->
                val match = pat.find(line)

                if (match != null) {
                    val range = if (match.groupValues[1] == "*") {
                        PAGE_RANGE

                    } else {
                        val parts = match.groupValues[1].split('-')
                        val start = parts[0].toInt()
                        IntRange(start, parts.getOrNull(1)?.toInt() ?: start)
                    }

                    // Allow empty or invalid page numbers to mark gaps, e.g. to map favorites to red and blue slots only.
                    val pageNums = match.groupValues[2]
                            .split(",")
                            .map { it.trim().toIntOrNull() }
                            .map { if (it == null || it !in PAGE_RANGE ) INVALID_PAGE else it }
                            .toIntArray()

                    for (num in range) {
                        byPage[num] = pageNums
                    }
                }
            }
        }
    }
}
