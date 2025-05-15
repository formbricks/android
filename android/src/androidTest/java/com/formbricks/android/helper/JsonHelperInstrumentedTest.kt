package com.formbricks.android.helper

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JsonHelperInstrumentedTest {
    @Test
    fun testMapToJsonElement_withPrimitives() {
        val map = mapOf(
            "string" to "value",
            "int" to 1,
            "double" to 2.5,
            "bool" to true
        )
        val json = mapToJsonElement(map)
        assertEquals("value", json.jsonObject["string"]?.jsonPrimitive?.content)
        assertEquals(1, json.jsonObject["int"]?.jsonPrimitive?.int)
        assertEquals(2.5, json.jsonObject["double"]?.jsonPrimitive?.double ?: 0.0, 0.0)
        assertEquals(true, json.jsonObject["bool"]?.jsonPrimitive?.boolean)
    }

    @Test
    fun testMapToJsonElement_withNestedMap() {
        val map = mapOf(
            "outer" to mapOf(
                "inner" to "inside"
            )
        )
        val json = mapToJsonElement(map)
        assertEquals("inside", json.jsonObject["outer"]?.jsonObject?.get("inner")?.jsonPrimitive?.content)
    }

    @Test
    fun testMapToJsonElement_withList() {
        val map = mapOf(
            "list" to listOf(1, 2, 3)
        )
        val json = mapToJsonElement(map)
        val arr = json.jsonObject["list"] as JsonArray
        assertEquals(3, arr.size)
        assertEquals(1, arr[0].jsonPrimitive.int)
        assertEquals(2, arr[1].jsonPrimitive.int)
        assertEquals(3, arr[2].jsonPrimitive.int)
    }

    @Test
    fun testMapToJsonElement_withNull() {
        val map = mapOf(
            "something" to null
        )
        val json = mapToJsonElement(map)
        assertTrue(json.jsonObject["something"] is JsonNull)
    }

    @Test
    fun testMapToJsonElement_withListOfMaps() {
        val map = mapOf(
            "list" to listOf(
                mapOf("a" to 1),
                mapOf("b" to 2)
            )
        )
        val json = mapToJsonElement(map)
        val arr = json.jsonObject["list"] as JsonArray
        assertEquals(2, arr.size)
        assertEquals(1, arr[0].jsonObject["a"]?.jsonPrimitive?.int)
        assertEquals(2, arr[1].jsonObject["b"]?.jsonPrimitive?.int)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMapToJsonElement_withUnsupportedType() {
        val map = mapOf("bad" to Any())
        mapToJsonElement(map)
    }

    @Test
    fun testMapToJsonElementItem_withPrimitives() {
        assertEquals(JsonPrimitive("hello"), mapToJsonElementItem("hello"))
        assertEquals(JsonPrimitive(42), mapToJsonElementItem(42))
        assertEquals(JsonPrimitive(true), mapToJsonElementItem(true))
    }

    @Test
    fun testMapToJsonElementItem_withNull() {
        assertTrue(mapToJsonElementItem(null) is JsonNull)
    }

    @Test
    fun testMapToJsonElementItem_withList() {
        val list = listOf("a", "b")
        val json = mapToJsonElementItem(list)
        assertTrue(json is JsonArray)
        assertEquals("a", (json as JsonArray)[0].jsonPrimitive.content)
        assertEquals("b", json[1].jsonPrimitive.content)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testMapToJsonElementItem_withUnsupportedType() {
        mapToJsonElementItem(Any())
    }
} 