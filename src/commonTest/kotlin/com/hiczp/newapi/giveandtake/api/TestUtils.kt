package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.http.content.*
import kotlinx.serialization.json.*

/** Respond with HTTP 200 and a JSON body. */
fun MockRequestHandleScope.respondJson(content: JsonElement) = respondJson(content.toString())

fun MockRequestHandleScope.respondJson(content: String) = respond(
    content = content,
    status = HttpStatusCode.OK,
    headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
)

/** A successful new-api response envelope without a data part. */
fun successResponse(): JsonObject = buildJsonObject {
    put("success", true)
    put("message", "")
}

/** A new-api business failure envelope. */
fun failureResponse(message: String): JsonObject = buildJsonObject {
    put("success", false)
    put("message", message)
}

/**
 * A channel fixture containing all modeled fields and additional response fields
 * that the client must ignore.
 */
fun newApiChannel(
    id: Int,
    usedQuota: Long,
    tag: String? = null,
    remark: String? = null,
): JsonObject = buildJsonObject {
    put("id", id)
    put("type", 1)
    put("openai_organization", JsonNull)
    put("test_model", JsonNull)
    put("name", "channel-$id")
    put("status", 1)
    put("weight", JsonNull)
    put("created_time", 0)
    put("test_time", 0)
    put("response_time", 0)
    put("base_url", JsonNull)
    put("other", "")
    put("balance", 0.0)
    put("balance_updated_time", 0)
    put("models", "")
    put("group", "default")
    put("used_quota", usedQuota)
    put("model_mapping", JsonNull)
    put("status_code_mapping", JsonNull)
    put("priority", JsonNull)
    put("auto_ban", JsonNull)
    put("other_info", "")
    put("tag", tag)
    put("setting", JsonNull)
    put("param_override", JsonNull)
    put("header_override", JsonNull)
    put("remark", remark)
    put(
        "channel_info",
        buildJsonObject {
            put("is_multi_key", false)
            put("multi_key_size", 0)
            put("multi_key_status_list", JsonNull)
            put("multi_key_polling_index", 0)
            put("multi_key_mode", "")
        },
    )
    put("settings", "{}")
}

/** Read the request body text at the engine level (content after Ktorfit + ContentNegotiation serialization). */
fun HttpRequestData.bodyAsText(): String = when (val content = body) {
    is TextContent -> content.text
    is ByteArrayContent -> content.bytes().decodeToString()
    else -> error("cannot read request body, unexpected content type: ${content::class.simpleName}")
}
