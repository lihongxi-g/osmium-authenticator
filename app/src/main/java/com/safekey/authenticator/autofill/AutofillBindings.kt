package com.safekey.authenticator.autofill

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * JSON codec for the stored "package name → account id" autofill bindings.
 * Lives in DataStore as a single string; corrupt data degrades to an empty
 * map instead of crashing the service.
 */
object AutofillBindings {

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(bindings: Map<String, String>): String = json.encodeToString(bindings)

    fun decode(raw: String?): Map<String, String> {
        if (raw.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString<Map<String, String>>(raw)
        } catch (_: Exception) {
            emptyMap()
        }
    }
}
