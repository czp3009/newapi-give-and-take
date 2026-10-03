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

/** Tests channel request parameters, response parsing and error propagation using [MockEngine]. */
class NewApiTest : NewApiTestSupport() {
    /** Channel fixture with modeled fields and additional fields that the client must ignore. */
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
        typeCounts: JsonObject = buildJsonObject {},
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
            assertEquals("/api/channel/", request.url.encodedPath)
            assertEquals("Bearer admin-token", request.headers[HttpHeaders.Authorization])
            assertEquals("1", request.url.parameters["p"])
            assertEquals("100", request.url.parameters["page_size"])
            assertEquals("id", request.url.parameters["sort_by"])
            assertEquals("asc", request.url.parameters["sort_order"])
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

        val api: NewApi = client(engine)

        val data = api.listChannels(page = 1, pageSize = 100, sortBy = "id", sortOrder = "asc").data!!

        assertEquals(1, data.total)
        assertEquals(1, data.page)
        assertEquals(100, data.pageSize)
        assertEquals(mapOf("1" to 1L), data.typeCounts)

        val channel = data.items.single()
        assertEquals(42L, channel.id)
        assertEquals("openai-main", channel.name)
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

        val api: NewApi = client(engine)

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
        val channelWithoutName = JsonObject(newApiChannel(id = 5, usedQuota = 7) - "name")
        val engine = MockEngine { _ ->
            respondJson(
                channelListEnvelope(
                    channelListData(listOf(channelWithoutName), total = 1, page = 1, pageSize = 100),
                ),
            )
        }

        val api: NewApi = client(engine)

        assertFailsWith<JsonConvertException> { api.listChannels(page = 1, pageSize = 100) }
    }

    @Test
    fun testBaseUrlWithoutTrailingSlashIsNormalized() = runTest {
        val engine = MockEngine { request ->
            assertEquals("http", request.url.protocol.name)
            assertEquals("localhost", request.url.host)
            assertEquals(3000, request.url.port)
            assertEquals("/api/channel/", request.url.encodedPath)
            respondJson(channelListEnvelope(channelListData(emptyList(), total = 0, page = 1, pageSize = 100)))
        }

        val api: NewApi = client(engine, baseUrl = "http://localhost:3000")

        api.listChannels(page = 1, pageSize = 100)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testMissingDataRejectsIncompleteList() = runTest {
        val engine = MockEngine { _ -> respondJson(successResponse()) }

        val client = client(engine)

        assertFailsWith<NewApiException> { client.listAllChannels() }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testHttpLevelErrorThrows() = runTest {
        // HTTP errors must propagate even when the body is plain text.
        val engine = MockEngine { _ ->
            respond(
                content = "bad gateway",
                status = HttpStatusCode.BadGateway,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }

        val api: NewApi = client(engine)

        assertFailsWith<ResponseException> { api.listChannels(page = 1, pageSize = 100) }
    }

    @Test
    fun testRequestFailurePropagates() = runTest {
        val engine = MockEngine { throw IOException("connection refused") }

        val api: NewApi = client(engine)

        val exception = assertFailsWith<IOException> { api.listChannels(page = 1, pageSize = 100) }
        assertEquals("connection refused", exception.message)
    }
}
