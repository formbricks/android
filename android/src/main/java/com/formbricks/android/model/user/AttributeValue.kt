package com.formbricks.android.model.user

import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonParseException
import com.google.gson.JsonPrimitive
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.annotations.JsonAdapter
import java.lang.reflect.Type

/**
 * Represents a user attribute value that can be a string or number.
 *
 * Attribute types are determined by the value type:
 * - String values -> string attribute
 * - Number values -> number attribute
 * - Use ISO 8601 date strings for date attributes
 *
 * On first write to a new attribute, the type is set based on the value type.
 * On subsequent writes, the value must match the existing attribute type.
 *
 * ```kotlin
 * val attributes = mapOf(
 *     "name" to AttributeValue.StringValue("John"),
 *     "age" to AttributeValue.NumberValue(30.0),
 *     "score" to AttributeValue.NumberValue(9.5)
 * )
 * ```
 */
@JsonAdapter(AttributeValueAdapter::class)
sealed class AttributeValue {
    data class StringValue(val value: String) : AttributeValue()
    data class NumberValue(val value: Double) : AttributeValue()

    /** The string representation of this attribute value, if it is a string. */
    val stringValue: String?
        get() = (this as? StringValue)?.value

    /** The numeric representation of this attribute value, if it is a number. */
    val numberValue: Double?
        get() = (this as? NumberValue)?.value

    companion object {
        fun string(value: String): AttributeValue = StringValue(value)
        fun number(value: Double): AttributeValue = NumberValue(value)
    }
}

/**
 * Gson adapter for [AttributeValue] that serializes/deserializes as the raw primitive value.
 * - `StringValue("hello")` serializes as `"hello"`
 * - `NumberValue(42.0)` serializes as `42.0`
 */
class AttributeValueAdapter : JsonSerializer<AttributeValue>, JsonDeserializer<AttributeValue> {
    override fun serialize(src: AttributeValue, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        return when (src) {
            is AttributeValue.StringValue -> JsonPrimitive(src.value)
            is AttributeValue.NumberValue -> JsonPrimitive(src.value)
        }
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): AttributeValue {
        if (json.isJsonPrimitive) {
            val primitive = json.asJsonPrimitive
            return when {
                primitive.isNumber -> AttributeValue.NumberValue(primitive.asDouble)
                primitive.isString -> AttributeValue.StringValue(primitive.asString)
                else -> throw JsonParseException("Expected String or Number for AttributeValue")
            }
        }
        throw JsonParseException("Expected String or Number for AttributeValue")
    }
}
