package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.util.network.*
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class HttpClientTest {
    @Test
    fun testHttpProxyReachesEngineWithoutChangingRequestUrl() = runTest {
        var requests = 0
        val mockEngineFactory = mockEngineFactory {
            requests++
            assertEquals("https://newapi.example.com/api/user/manage", it.url.toString())
            respondJson(successResponse())
        }
        createHttpClient("admin-token", mockEngineFactory, proxyUrl = "http://127.0.0.1:3128").use { httpClient ->
            val proxyConfig = assertNotNull(httpClient.engine.config.proxy)
            assertEquals(ProxyType.HTTP, proxyConfig.type)
            val networkAddress = proxyConfig.resolveAddress()
            assertEquals("127.0.0.1", networkAddress.address)
            assertEquals(3128, networkAddress.port)
            assertEquals(
                successResponse().toString(),
                httpClient.get("https://newapi.example.com/api/user/manage").bodyAsText()
            )
            assertEquals(1, requests)
        }
    }

    @Test
    fun testMissingAndEmptyProxyLeaveEngineProxyUnset() = runTest {
        listOf(null, "").forEach { proxyUrl ->
            createHttpClient(
                "admin-token",
                mockEngineFactory { error("No request expected") },
                proxyUrl
            ).use { httpClient ->
                assertNull(httpClient.engine.config.proxy)
            }
        }
    }

    private fun mockEngineFactory(handler: MockRequestHandler): HttpClientEngineFactory<MockEngineConfig> =
        object : HttpClientEngineFactory<MockEngineConfig> {
            override fun create(block: MockEngineConfig.() -> Unit): HttpClientEngine = MockEngine(
                MockEngineConfig().apply {
                    addHandler(handler)
                    block()
                },
            )
        }
}
