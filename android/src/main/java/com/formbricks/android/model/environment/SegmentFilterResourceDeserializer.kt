package com.formbricks.android.model.environment

import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class SegmentFilterResourceDeserializer : JsonDeserializer<SegmentFilterResource> {
    override fun deserialize(
        json: JsonElement,
        typeOfT: Type,
        context: JsonDeserializationContext
    ): SegmentFilterResource {
        return if (json.isJsonArray) {
            // JSON is an array → treat it as a Group
            val listType = object : TypeToken<List<SegmentFilter>>() {}.type
            val filters: List<SegmentFilter> = context.deserialize(json, listType)
            SegmentFilterResource.Group(filters)
        } else {
            // JSON is an object → treat it as a Primitive
            val prim: SegmentPrimitiveFilter =
                context.deserialize(json, SegmentPrimitiveFilter::class.java)
            SegmentFilterResource.Primitive(prim)
        }
    }
}
