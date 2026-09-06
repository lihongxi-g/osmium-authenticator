package com.safekey.authenticator.totp.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Strips a UTF-8 BOM (Android file reads may keep it) and surrounding whitespace. */
internal fun String.cleanJsonText(): String = removePrefix("\uFEFF").trim()

/** Parses [content] as JSON. Callers wrap [kotlinx.serialization.json.Json.parseToJsonElement] failures. */
internal fun parseJsonElement(content: String): JsonElement =
    Json.parseToJsonElement(content.cleanJsonText())

/** Text value of [key]; numbers are accepted ("6" for a numeric 6). Null when absent/null/non-primitive. */
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.content?.takeIf { it.isNotEmpty() }

/** Integer value of [key], tolerating JSON numbers and numeric strings. */
internal fun JsonObject.intOrNull(key: String): Int? =
    (this[key] as? JsonPrimitive)?.content?.toIntOrNull()

/** Long value of [key], tolerating JSON numbers and numeric strings. */
internal fun JsonObject.longOrNull(key: String): Long? =
    (this[key] as? JsonPrimitive)?.content?.toLongOrNull()

internal fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

internal fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray

internal val JsonElement.asObj: JsonObject? get() = this as? JsonObject

internal val JsonElement.asArr: JsonArray? get() = this as? JsonArray
