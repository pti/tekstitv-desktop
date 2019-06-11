package fi.reuna.tekstitv.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.awt.Color
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalUnit

class DurationAdapter(val unit: TemporalUnit = ChronoUnit.SECONDS): JsonAdapter<Duration>() {

    override fun fromJson(reader: JsonReader): Duration {
        return Duration.of(reader.nextLong(), unit)
    }

    override fun toJson(writer: JsonWriter, value: Duration?) {
        writer.value(value?.get(unit))
    }
}
