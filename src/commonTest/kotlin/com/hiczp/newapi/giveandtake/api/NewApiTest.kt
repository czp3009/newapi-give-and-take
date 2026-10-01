package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.serialization.*
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/**
 * Tests for the channel endpoint, with a MockEngine simulating the new-api
 * server. The response structure mirrors new-api `controller/channel.go` ->
 * `GetAllChannels`.
 */
class NewApiTest {

    /** A full channel as serialized by new-api's `model.Channel`. */
    private val fullChannel: JsonObject = buildJsonObject {
        put("id", 42)
        put("type", 1)
        put("openai_organization", JsonNull)
        put("test_model", "gpt-4o")
        put("status", 1)
        put("name", "openai-main")
        put("weight", 10)
        put("created_time", 1710000000L)
        put("test_time", 1710001000L)
        put("response_time", 812)
        put("base_url", "https://api.openai.com")
        put("other", "")
        put("balance", 12.34)
        put("balance_updated_time", 1710002000L)
        put("models", "gpt-4o,gpt-4o-mini")
        put("group", "default,vip")
        put("used_quota", 123456789L)
        put("model_mapping", "{}")
        put("status_code_mapping", "")
        put("priority", 10)
        put("auto_ban", 1)
        put("other_info", "{}")
        put("tag", "contributor:alice")
        put("setting", "{}")
        put("param_override", "{}")
        put("header_override", "")
        put("remark", "由 Alice 贡献")
        put(
            "channel_info",
            buildJsonObject {
                put("is_multi_key", false)
                put("multi_key_size", 0)
                put("multi_key_status_list", JsonNull)
                put("multi_key_disabled_reason", JsonNull)
                put("multi_key_disabled_time", JsonNull)
                put("multi_key_polling_index", 0)
                put("multi_key_mode", "random")
            },
        )
        put("settings", "{}")
    }

    /** The `data` part of the channel list response. */
    private fun channelListData(
        items: List<JsonObject>,
        total: Long,
        page: Int,
        pageSize: Int,
        typeCounts: JsonObject = buildJsonObject { },
    ): JsonObject = buildJsonObject {
        put("items", JsonArray(items))
        put("total", total)
        put("page", page)
        put("page_size", pageSize)
        put("type_counts", typeCounts)
    }

    private fun channelListEnvelope(data: JsonObject): JsonObject = buildJsonObject {
        put("success", true)
        put("message", "")
        put("data", data)
    }

    @Test
    fun testGetChannelsSendsCorrectRequestAndParsesDetails() = runTest {
        val engine = MockEngine { request ->
            // Verify the request: path (registered in gin as GET /api/channel/,
            // with trailing slash), auth header and query parameters.
            assertEquals("/api/channel/", request.url.encodedPath)
            assertEquals("Bearer admin-token", request.headers[HttpHeaders.Authorization])
            assertEquals("1", request.url.parameters["p"])
            assertEquals("100", request.url.parameters["page_size"])
            assertEquals("id", request.url.parameters["sort_by"])
            assertEquals("asc", request.url.parameters["sort_order"])
            // Unused filters must not appear in the query string.
            assertNull(request.url.parameters["status"])
            assertNull(request.url.parameters["type"])
            assertNull(request.url.parameters["group"])
            assertNull(request.url.parameters["tag_mode"])

            respondJson(
                channelListEnvelope(
                    channelListData(
                        items = listOf(fullChannel),
                        total = 1,
                        page = 1,
                        pageSize = 100,
                        typeCounts = buildJsonObject { put("1", 1L) },
                    ),
                ),
            )
        }

        // The raw endpoint is reachable through delegation from NewApiClient.
        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        val data = api.listChannels(page = 1, pageSize = 100, sortBy = "id", sortOrder = "asc").data!!

        assertEquals(1, data.total)
        assertEquals(1, data.page)
        assertEquals(100, data.pageSize)
        assertEquals(mapOf("1" to 1L), data.typeCounts)

        // Only the modeled fields are readable; the rest of the server
        // payload above is ignored.
        val channel = data.items.single()
        assertEquals(42, channel.id)
        assertEquals("openai-main", channel.name)
        // The fields this project cares about.
        assertEquals("contributor:alice", channel.tag)
        assertEquals("由 Alice 贡献", channel.remark)
        assertEquals(123456789L, channel.usedQuota)
    }

    @Test
    fun testGetChannelsSendsAllFilters() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/channel/", request.url.encodedPath)
            assertEquals("2", request.url.parameters["p"])
            assertEquals("50", request.url.parameters["page_size"])
            assertEquals("enabled", request.url.parameters["status"])
            assertEquals("1", request.url.parameters["type"])
            assertEquals("vip", request.url.parameters["group"])
            assertEquals("true", request.url.parameters["tag_mode"])
            assertEquals("true", request.url.parameters["id_sort"])
            assertEquals("priority", request.url.parameters["sort_by"])
            assertEquals("desc", request.url.parameters["sort_order"])

            respondJson(channelListEnvelope(channelListData(emptyList(), total = 0, page = 2, pageSize = 50)))
        }

        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        api.listChannels(
            page = 2,
            pageSize = 50,
            status = "enabled",
            type = 1,
            group = "vip",
            tagMode = true,
            idSort = true,
            sortBy = "priority",
            sortOrder = "desc",
        )
    }

    @Test
    fun testGetChannelsMissingRequiredFieldThrows() = runTest {
        // Required fields are never omitted by the server (no `omitempty` in
        // Go), so a missing one is a schema mismatch and must fail loudly
        // instead of falling back to a default.
        val channelWithoutName = JsonObject(newApiChannel(id = 5, usedQuota = 7) - "name")
        val engine = MockEngine { _ ->
            respondJson(
                channelListEnvelope(
                    channelListData(listOf(channelWithoutName), total = 1, page = 1, pageSize = 100),
                ),
            )
        }

        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        assertFailsWith<JsonConvertException> { api.listChannels(page = 1, pageSize = 100) }
    }

    @Test
    fun testBaseUrlWithoutTrailingSlashIsNormalized() = runTest {
        val engine = MockEngine { request ->
            assertEquals("https", request.url.protocol.name)
            assertEquals("newapi.example.com", request.url.host)
            assertEquals("/api/channel/", request.url.encodedPath)
            respondJson(channelListEnvelope(channelListData(emptyList(), total = 0, page = 1, pageSize = 100)))
        }

        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        api.listChannels(page = 1, pageSize = 100)
    }

    @Test
    fun testMissingDataIsTreatedAsEmptyPage() = runTest {
        // success=true but no data at all.
        val engine = MockEngine { _ -> respondJson(successResponse()) }

        val client = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        assertEquals(emptyList(), client.listAllChannels())
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testHttpLevelErrorThrows() = runTest {
        // A non-2xx response (e.g. from a reverse proxy in front of new-api)
        // is an HTTP level error, not a business envelope.
        val engine = MockEngine { _ ->
            respond(
                content = "bad gateway",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        assertFailsWith<ResponseException> { api.listChannels(page = 1, pageSize = 100) }
    }

    @Test
    fun testRequestFailurePropagates() = runTest {
        val engine = MockEngine { throw IOException("connection refused") }

        val api: NewApi = NewApiClient.create(NewApiConfig("https://newapi.example.com", "admin-token"), engine)

        val exception = assertFailsWith<IOException> { api.listChannels(page = 1, pageSize = 100) }
        assertEquals("connection refused", exception.message)
    }
}
