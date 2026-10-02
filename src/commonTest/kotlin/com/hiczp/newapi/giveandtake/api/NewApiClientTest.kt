package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.mock.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Tests for the pagination logic and argument validation of [NewApiClient]. */
class NewApiClientTest {
    private fun channelList(
        items: List<JsonObject>,
        total: Long,
        page: Int,
        pageSize: Int,
    ): JsonObject = buildJsonObject {
        put("success", true)
        put("message", "")
        put(
            "data",
            buildJsonObject {
                put("items", JsonArray(items))
                put("total", total)
                put("page", page)
                put("page_size", pageSize)
                putJsonObject("type_counts") {}
            },
        )
    }

    private fun client(engine: MockEngine): NewApiClient = NewApiClient.create(
        NewApiConfig("https://newapi.example.com/", "admin-token"),
        engine,
    )

    @Test
    fun testListAllChannelsIteratesAllPages() = runTest {
        val engine = MockEngine { request ->
            assertEquals("/api/channel/", request.url.encodedPath)
            val page = request.url.parameters["p"]!!.toInt()
            assertEquals("2", request.url.parameters["page_size"])

            val content = when (page) {
                1 -> channelList(
                    listOf(
                        newApiChannel(1, 100, tag = "contributor:1", remark = "contributed by 1"),
                        newApiChannel(2, 200, tag = "contributor:2", remark = "contributed by 2"),
                    ),
                    total = 3,
                    page = 1,
                    pageSize = 2,
                )

                2 -> channelList(
                    listOf(newApiChannel(3, 300, tag = "contributor:3", remark = "contributed by 3")),
                    total = 3,
                    page = 2,
                    pageSize = 2,
                )

                else -> error("unexpected page: $page")
            }

            respondJson(content)
        }

        val channels = client(engine).listAllChannels(pageSize = 2)

        assertEquals(listOf(1, 2, 3), channels.map { it.id })
        assertEquals(listOf(100L, 200L, 300L), channels.map { it.usedQuota })
        assertEquals(listOf("contributor:1", "contributor:2", "contributor:3"), channels.map { it.tag })
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun testListAllChannelsStopsOnZeroTotal() = runTest {
        val engine = MockEngine { _ -> respondJson(channelList(emptyList(), total = 0, page = 1, pageSize = 100)) }

        assertEquals(emptyList(), client(engine).listAllChannels())
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testListAllChannelsMakesSingleRequestWhenOnLastPage() = runTest {
        val engine = MockEngine { _ ->
            respondJson(
                channelList(
                    listOf(
                        newApiChannel(1, 100, tag = "contributor:1"),
                        newApiChannel(2, 200, tag = "contributor:2"),
                    ),
                    total = 2,
                    page = 1,
                    pageSize = 2,
                ),
            )
        }

        assertEquals(listOf(1, 2), client(engine).listAllChannels(pageSize = 2).map { it.id })
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testListAllChannelsRequestsUntilLastPageDerivedFromTotal() = runTest {
        // The server claims total = 10 but only 3 channels exist; pagination
        // ends at the last page computed from total (5 with page size 2), the
        // missing channels simply come back as empty pages.
        val engine = MockEngine { request ->
            val content = when (val page = request.url.parameters["p"]!!.toInt()) {
                1 -> channelList(
                    listOf(newApiChannel(1, 100), newApiChannel(2, 200)),
                    total = 10,
                    page = 1,
                    pageSize = 2,
                )

                2 -> channelList(listOf(newApiChannel(3, 300)), total = 10, page = 2, pageSize = 2)
                else -> channelList(emptyList(), total = 10, page = page, pageSize = 2)
            }
            respondJson(content)
        }

        assertEquals(listOf(1, 2, 3), client(engine).listAllChannels(pageSize = 2).map { it.id })
        assertEquals(5, engine.requestHistory.size)
    }

    @Test
    fun testListChannelsThrowsOnBusinessError() = runTest {
        val engine = MockEngine { _ -> respondJson(failureResponse("获取渠道列表失败，请稍后重试")) }

        val exception = assertFailsWith<NewApiException> { client(engine).listChannels(page = 1, pageSize = 100) }
        assertEquals("获取渠道列表失败，请稍后重试", exception.message)
    }

    @Test
    fun testListAllChannelsValidatesPageSize() = runTest {
        val engine = MockEngine { error("no request should be made") }
        val client = client(engine)

        assertFailsWith<IllegalArgumentException> { client.listAllChannels(pageSize = 0) }
        assertFailsWith<IllegalArgumentException> { client.listAllChannels(pageSize = 101) }
    }

    @Test
    fun testListAllChannelsUsesMaxPageSizeByDefault() = runTest {
        val engine = MockEngine { request ->
            assertEquals("1", request.url.parameters["p"])
            assertEquals("100", request.url.parameters["page_size"])
            assertEquals("true", request.url.parameters["id_sort"])
            assertNull(request.url.parameters["sort_by"])
            assertNull(request.url.parameters["sort_order"])
            respondJson(channelList(emptyList(), total = 0, page = 1, pageSize = 100))
        }

        client(engine).listAllChannels()
    }
}
