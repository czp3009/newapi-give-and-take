package com.hiczp.newapi.giveandtake.api

import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Creates a client with JSON conversion, bearer authentication, fixed timeouts and new-api error handling.
 * Business failures throw [NewApiException]; HTTP errors use Ktor's [ResponseException].
 * A non-empty [proxyUrl] configures an HTTP proxy for the engine.
 * The caller closes the client, which also owns its engine.
 */
internal fun createHttpClient(
    accessToken: String,
    engineFactory: HttpClientEngineFactory<*> = platformHttpEngineFactory(),
    proxyUrl: String? = null,
): HttpClient = HttpClient(engineFactory) {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    engine {
        proxy = proxyUrl?.takeIf { it.isNotEmpty() }?.let { ProxyBuilder.http(it) }
    }
    expectSuccess = true
    install(HttpTimeout) {
        requestTimeoutMillis = 30_000
        connectTimeoutMillis = 5_000
        // Curl does not support socket timeouts; this setting applies to engines such as CIO.
        socketTimeoutMillis = 15_000
    }
    install(ContentNegotiation) {
        json(json)
    }
    install(DefaultRequest) {
        headers.append(HttpHeaders.Authorization, "Bearer $accessToken")
        contentType(ContentType.Application.Json)
    }
    // HTTP errors may contain non-JSON bodies; leave those to expectSuccess.
    install(
        createClientPlugin("NewApiBusinessError") {
            onResponse { response ->
                if (!response.status.isSuccess()) return@onResponse
                val apiResponse = json.decodeFromString(
                    ApiResponse.serializer(JsonElement.serializer()),
                    response.bodyAsText(),
                )
                if (!apiResponse.success) {
                    throw NewApiException(apiResponse.message.ifBlank { "new-api request failed" })
                }
            }
        },
    )
}
