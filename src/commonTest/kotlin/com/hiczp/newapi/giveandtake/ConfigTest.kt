package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.api.Channel
import com.hiczp.newapi.giveandtake.file.Config
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class ConfigTest {
    private fun channel(remark: String?, tag: String? = null) = Channel(1, "channel", 0, tag, remark)

    @Test
    fun testConfigurationPrioritySkipsOnlyEmptyOrMissingValues() {
        assertEquals("cli", configurationValue("setting", "cli", "environment", "file"))
        assertEquals("environment", configurationValue("setting", "", "environment", "file"))
        assertEquals("file", configurationValue("setting", null, "", "file"))
        assertEquals(" ", configurationValue("setting", " ", "environment", "file"))
        val exception =
            assertFailsWith<IllegalArgumentException> { configurationValue("newApi.baseUrl", null, "", null) }
        assertEquals("Missing configuration: newApi.baseUrl", exception.message)
    }

    @Test
    fun testMissingAndNullFileValuesRemainUnconfigured() {
        val config =
            Json.decodeFromString<Config>("""{"newApi":{"baseUrl":null},"tracking":{"field":"","pattern":null}}""")
        assertEquals(null, config.newApi.baseUrl)
        assertEquals(null, config.newApi.accessToken)
        assertEquals("", config.tracking.field)
        assertEquals(null, config.tracking.pattern)
    }

    @Test
    fun testStrictConfigAndRegexEscapingRoundTrip() {
        val config = Config(
            Config.NewApi("http://localhost:3000", "placeholder"),
            Config.Tracking("remark", """user:(?<userId>\d+)"""),
        )
        val encoded = Json.encodeToString(config)
        assertTrue(encoded.contains("""\\d"""))
        val decoded = Json.decodeFromString<Config>(encoded)
        assertEquals(config.newApi.baseUrl, decoded.newApi.baseUrl)
        assertEquals(config.newApi.accessToken, decoded.newApi.accessToken)
        assertEquals(config.tracking, decoded.tracking)
        assertEquals(
            1L,
            UserMatcher(
                requireNotNull(decoded.tracking.field),
                requireNotNull(decoded.tracking.pattern)
            ).userId(channel("user:1"))
        )
        assertEquals(null, Json.decodeFromString<Config>("{}").newApi.baseUrl)
        assertFailsWith<SerializationException> {
            val withReward =
                JsonObject(Json.parseToJsonElement(encoded).jsonObject + ("reward" to JsonObject(emptyMap())))
            Json.decodeFromString<Config>(withReward.toString())
        }
    }
}
