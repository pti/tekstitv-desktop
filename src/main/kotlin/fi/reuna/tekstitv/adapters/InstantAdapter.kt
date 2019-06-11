package fi.reuna.tekstitv.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.time.Instant
import java.time.format.DateTimeFormatter

class InstantAdapter: JsonAdapter<Instant>() {

    override fun fromJson(reader: JsonReader): Instant? {
        return Instant.parse(reader.nextString())
    }

    override fun toJson(writer: JsonWriter, value: Instant?) {
        writer.value(if (value == null) null else DateTimeFormatter.ISO_INSTANT.format(value))
    }
}
