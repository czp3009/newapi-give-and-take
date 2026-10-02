package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Tests for user quota request serialization and business errors using [MockEngine].
 */
class UserApiTest {
    private fun client(engine: MockEngine): NewApiClient = NewApiClient.create(
        NewApiConfig("https://newapi.example.com/", "admin-token"),
        engine,
    )

    @Test
    fun testManageUserSendsCorrectRequest() = runTest {
        val engine = MockEngine { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("/api/user/manage", request.url.encodedPath)
            assertEquals("Bearer admin-token", request.headers[HttpHeaders.Authorization])
            // The serialized outgoing body carries the JSON content type.
            assertTrue(request.body.contentType.toString().startsWith("application/json"))

            // Verify the request body JSON (key order insensitive).
            val expected = buildJsonObject {
                put("id", 7)
                put("action", "add_quota")
                put("mode", "add")
                put("value", 500000L)
            }
            assertEquals(expected, Json.parseToJsonElement(request.bodyAsText()).jsonObject)

            respondJson(successResponse())
        }

        client(engine).manageUser(ManageUserRequest(id = 7, value = 500000))
    }

    @Test
    fun testManageUserThrowsOnBusinessError() = runTest {
        val engine = MockEngine { _ -> respondJson(failureResponse("无权进行此操作，请升级权限")) }

        val exception =
            assertFailsWith<NewApiException> { client(engine).manageUser(ManageUserRequest(id = 7, value = 100)) }
        assertEquals("无权进行此操作，请升级权限", exception.message)
    }

    @Test
    fun testManageUserSerializesSubtractAndOverrideModes() = runTest {
        val bodies = mutableListOf<JsonElement>()
        val engine = MockEngine { request ->
            bodies += Json.parseToJsonElement(request.bodyAsText())
            respondJson(successResponse())
        }

        val client = client(engine)

        client.manageUser(ManageUserRequest(id = 7, mode = QuotaAdjustMode.SUBTRACT, value = 5))
        val subtractBody = bodies[0].jsonObject
        assertEquals("subtract", subtractBody["mode"]!!.jsonPrimitive.content)
        assertEquals("add_quota", subtractBody["action"]!!.jsonPrimitive.content)

        client.manageUser(ManageUserRequest(id = 7, mode = QuotaAdjustMode.OVERRIDE, value = 500))
        val overrideBody = bodies[1].jsonObject
        assertEquals("override", overrideBody["mode"]!!.jsonPrimitive.content)
        assertEquals(500, overrideBody["value"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun testBusinessErrorWithBlankMessageFallsBackToDefault() = runTest {
        val engine = MockEngine { _ ->
            respondJson(
                buildJsonObject {
                    put("success", false)
                    put("message", "")
                },
            )
        }

        val exception =
            assertFailsWith<NewApiException> { client(engine).manageUser(ManageUserRequest(id = 7, value = 100)) }
        assertEquals("new-api request failed", exception.message)
    }
}
