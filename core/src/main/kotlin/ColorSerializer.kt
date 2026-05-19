package top.e404.statimg

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object ColorSerializer : KSerializer<Int> {
    override val descriptor = PrimitiveSerialDescriptor(this::class.java.name, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Int) = encoder.encodeString(value.toString(16))

    override fun deserialize(decoder: Decoder): Int = decoder.decodeString().toLong(16).toInt()
}