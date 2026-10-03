package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Tests pagination, individual channel lookup and borrowed HTTP client ownership in [NewApiClient]. */
class NewApiClientTest : NewApiTestSupport() {
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

        assertEquals(listOf(1L, 2L, 3L), channels.map { it.id })
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

        assertEquals(listOf(1L, 2L), client(engine).listAllChannels(pageSize = 2).map { it.id })
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testListAllChannelsRequestsUntilLastPageDerivedFromTotal() = runTest {
        // The server claims total = 10 but only 3 channels exist; pagination ends at page 5, with missing channels returned as empty pages.
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

        assertEquals(listOf(1L, 2L, 3L), client(engine).listAllChannels(pageSize = 2).map { it.id })
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

    @Test
    fun testDeduplicatesIdsWithinAndAcrossPagesKeepingFirstOccurrence() = runTest {
        val engine = MockEngine { request ->
            val response = when (val page = request.url.parameters["p"]!!.toInt()) {
                1 -> channelList(
                    listOf(
                        newApiChannel(Long.MAX_VALUE, 40),
                        newApiChannel(Long.MAX_VALUE, 999),
                        newApiChannel(4294967297L, 30)
                    ),
                    total = 6,
                    page = page,
                    pageSize = 3,
                )

                2 -> channelList(
                    listOf(newApiChannel(4294967297L, 999), newApiChannel(2, 20), newApiChannel(1, 10)),
                    total = 7,
                    page = page,
                    pageSize = 3,
                )

                3 -> channelList(listOf(newApiChannel(1, 999)), total = 7, page = page, pageSize = 3)
                else -> error("unexpected page: $page")
            }
            respondJson(response)
        }
        val client = client(engine)
        repeat(2) {
            val channels = client.listAllChannels(pageSize = 3)
            assertEquals(listOf(Long.MAX_VALUE, 4294967297L, 2L, 1L), channels.map { it.id })
            assertEquals(listOf(40L, 30L, 20L, 10L), channels.map { it.usedQuota })
        }
        assertEquals(6, engine.requestHistory.size)
    }

    @Test
    fun testStopsUsingLatestTotalWhenChannelsAreDeleted() = runTest {
        val engine = MockEngine { request ->
            val response = when (val page = request.url.parameters["p"]!!.toInt()) {
                1 -> channelList(
                    listOf(newApiChannel(5, 50), newApiChannel(4, 40)),
                    total = 5,
                    page = page,
                    pageSize = 2,
                )

                2 -> channelList(listOf(newApiChannel(1, 10)), total = 3, page = page, pageSize = 2)
                else -> error("unexpected page: $page")
            }
            respondJson(response)
        }
        assertEquals(listOf(5L, 4L, 1L), client(engine).listAllChannels(pageSize = 2).map { it.id })
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun testFindChannelPreservesLargeIdsAndChannelDetails() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Get, request.method)
            assertEquals("/api/channel/${Long.MAX_VALUE}", request.url.encodedPath)
            respondJson(buildJsonObject {
                put("success", true)
                put("message", "")
                put("data", newApiChannel(Long.MAX_VALUE, 150, remark = "user:1"))
            })
        }
        val channel = client(engine).findChannel(Long.MAX_VALUE)!!
        assertEquals(Long.MAX_VALUE, channel.id)
        assertEquals(150L, channel.usedQuota)
        assertEquals("user:1", channel.remark)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testFindChannelReturnsNullForRecordNotFound() = runTest {
        val engine = MockEngine { respondJson(failureResponse("record not found")) }
        assertNull(client(engine).findChannel(42))
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testFindChannelPropagatesOtherBusinessFailures() = runTest {
        val engine = MockEngine { respondJson(failureResponse("denied")) }
        val exception = assertFailsWith<NewApiException> { client(engine).findChannel(42) }
        assertEquals("denied", exception.message)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testFindChannelRejectsMissingResponseData() = runTest {
        val engine = MockEngine { respondJson(successResponse()) }
        val exception = assertFailsWith<NewApiException> { client(engine).findChannel(42) }
        assertEquals("Channel response has no data", exception.message)
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testFindChannelDoesNotTreatHttpNotFoundAsChannelRemoval() = runTest {
        val engine = MockEngine { respond("route not found", HttpStatusCode.NotFound) }
        assertFailsWith<ClientRequestException> { client(engine).findChannel(42) }
        assertEquals(1, engine.requestHistory.size)
    }

    @Test
    fun testCallerOwnsHttpClientSharedAcrossApiWrappersAndFailures() = runTest {
        val baseUrl = "https://newapi.example.com"
        var requests = 0
        var closes = 0
        val engineClosed = CompletableDeferred<Unit>()
        val httpClient = mockNewApiHttpClient("admin-token", onClosed = {
            closes++
            engineClosed.complete(Unit)
        }) {
            requests++
            if (requests == 1) respondJson(failureResponse("denied")) else respondJson(successResponse())
        }
        try {
            val first = NewApiClient.create(baseUrl, httpClient)
            val second = NewApiClient.create(baseUrl, httpClient)
            assertEquals(0, closes)
            assertFailsWith<NewApiException> { first.manageUser(ManageUserRequest(1, value = 10)) }
            assertEquals(0, closes)
            second.manageUser(ManageUserRequest(2, value = 20))
            first.manageUser(ManageUserRequest(1, value = 10))
            assertEquals(3, requests)
            assertEquals(0, closes)
        } finally {
            httpClient.close()
        }
        engineClosed.await()
        assertEquals(1, closes)
    }
}
