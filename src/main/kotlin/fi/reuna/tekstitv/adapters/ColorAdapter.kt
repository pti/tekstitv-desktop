package fi.reuna.tekstitv.adapters

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import java.awt.Color

class ColorAdapter: JsonAdapter<Color>() {

    override fun fromJson(reader: JsonReader): Color {
        return Color(reader.nextString().toInt(16))
    }

    override fun toJson(writer: JsonWriter, value: Color?) {
        writer.value(value?.rgb?.toString(16))
    }
}
