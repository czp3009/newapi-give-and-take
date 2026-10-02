package com.hiczp.newapi.giveandtake.api

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.api.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/** Connection configuration of a new-api site. */
class NewApiConfig(
    /** Site root URL, e.g. `https://newapi.example.com/` (trailing slash optional). */
    val baseUrl: String,
    /** Admin access token, sent as the Authorization: Bearer header. */
    val accessToken: String,
)

/**
 * Delegates admin endpoints to [NewApi] and adds [listAllChannels] for pagination.
 *
 * The underlying HTTP client is retained internally; no close method is exposed.
 */
class NewApiClient private constructor(
    api: NewApi,
) : NewApi by api {
    /**
     * Collect channel pages starting at page 1, requesting descending id order
     * with `id_sort=true`. Items are appended in response order without sorting
     * or deduplication. Concurrent server changes can cause omissions or duplicates.
     *
     * The last page is computed from each response's total and page size. Empty
     * item lists do not stop pagination before that page. Missing or null data
     * ends pagination and returns the items already collected. The response page
     * size must be positive; it is not validated locally.
     *
     * @param pageSize Requested page size, from 1 through [MAX_PAGE_SIZE].
     * @throws IllegalArgumentException If [pageSize] is outside the accepted range.
     */
    suspend fun listAllChannels(pageSize: Int = MAX_PAGE_SIZE): List<Channel> = flow {
        require(pageSize in 1..MAX_PAGE_SIZE) { "page_size must be within 1..$MAX_PAGE_SIZE (server cap)" }

        var page = 1
        while (true) {
            val data = listChannels(page = page, pageSize = pageSize, idSort = true).data ?: break
            data.items.forEach { emit(it) }
            val lastPage = (data.total + data.pageSize - 1) / data.pageSize
            if (page >= lastPage) {
                break
            }
            page++
        }
    }.toList()

    companion object {
        /** Maximum page size accepted by [listAllChannels], also used as its default. */
        const val MAX_PAGE_SIZE = 100

        /** Create a client using [platformHttpEngine]; see the custom-engine overload for error handling. */
        fun create(config: NewApiConfig): NewApiClient = create(config, platformHttpEngine())

        /**
         * Create a client on a custom engine.
         *
         * Ktor's [ContentNegotiation] serializes request bodies and deserializes
         * typed responses, ignoring unknown JSON fields and encoding defaults.
         * A response interceptor decodes each 2xx envelope and throws [NewApiException]
         * when `success=false`.
         *
         * Ktor's `expectSuccess` validation reports HTTP errors as [ResponseException].
         * Transport and deserialization errors propagate to callers.
         */
        fun create(config: NewApiConfig, engine: HttpClientEngine): NewApiClient {
            val json = Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            }

            val client = HttpClient(engine) {
                expectSuccess = true

                install(ContentNegotiation) {
                    json(json)
                }
                install(DefaultRequest) {
                    headers.append(HttpHeaders.Authorization, "Bearer ${config.accessToken}")
                    // Select the JSON converter for request bodies.
                    contentType(ContentType.Application.Json)
                }
                // Inspect only 2xx envelopes; HTTP errors may contain non-JSON bodies.
                // Cached response bodies remain available for typed deserialization.
                install(
                    createClientPlugin("NewApiBusinessError") {
                        onResponse { response ->
                            if (!response.status.isSuccess()) {
                                return@onResponse
                            }

                            val envelope = json.decodeFromString(
                                ApiResponse.serializer(JsonElement.serializer()),
                                response.bodyAsText(),
                            )
                            if (!envelope.success) {
                                throw NewApiException(envelope.message.ifBlank { "new-api request failed" })
                            }
                        }
                    },
                )
            }

            val ktorfit = Ktorfit.Builder()
                .baseUrl(config.baseUrl.trimEnd('/') + "/")
                .httpClient(client)
                .build()

            return NewApiClient(ktorfit.createNewApi())
        }
    }
}
