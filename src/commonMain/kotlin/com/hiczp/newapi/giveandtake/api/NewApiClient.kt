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
    /** Site root URL, e.g. https://newapi.example.com/ (trailing slash optional). */
    val baseUrl: String,
    /** Admin access token, sent as the Authorization: Bearer header. */
    val accessToken: String,
)

/**
 * Ktorfit interface to the new-api admin API, plus [listAllChannels], which
 * iterates all channel pages and unwraps the response envelope.
 */
class NewApiClient private constructor(
    api: NewApi,
) : NewApi by api {
    /**
     * Iterate all channel pages automatically and return every channel, in
     * descending id order (newest first, the `id_sort` parameter), so that
     * pagination is stable. The server returns the full channel detail per
     * item, of which only id / name / used_quota / tag / remark are mapped.
     */
    suspend fun listAllChannels(pageSize: Int = MAX_PAGE_SIZE): List<Channel> = flow {
        require(pageSize in 1..MAX_PAGE_SIZE) { "page_size must be within 1..$MAX_PAGE_SIZE (server cap)" }

        var page = 1
        while (true) {
            // The server always wraps the page data on success; a missing data
            // part is treated defensively as an empty page.
            val data = listChannels(page = page, pageSize = pageSize, idSort = true).data ?: break
            data.items.forEach { emit(it) }
            // The server-side total (with the page size the server actually
            // used) tells which page is the last one.
            val lastPage = (data.total + data.pageSize - 1) / data.pageSize
            if (page >= lastPage) break
            page++
        }
    }.toList()

    companion object {
        /** Hard limit the new-api server enforces on the page size. */
        const val MAX_PAGE_SIZE = 100

        /**
         * Create a client on the platform default HTTP engine.
         *
         * Serialization is fully delegated to Ktor's official ContentNegotiation
         * converter (no custom Ktorfit converters); a response interceptor
         * turns new-api business failures into [NewApiException], and
         * `ExpectSuccess` turns non-2xx responses into
         * [io.ktor.client.plugins.ResponseException].
         */
        fun create(config: NewApiConfig): NewApiClient = create(config, platformHttpEngine())

        /**
         * Create a client on a custom engine; the seam tests use to inject a
         * MockEngine simulating the new-api server.
         */
        fun create(config: NewApiConfig, engine: HttpClientEngine): NewApiClient {
            val json = Json {
                ignoreUnknownKeys = true // tolerate fields added by the server in the future
                encodeDefaults = true // make sure defaulted request fields (action/mode) are serialized
            }

            val client = HttpClient(engine) {
                expectSuccess = true

                // The official kotlinx serialization converter, used for both
                // @Body request parameters and envelope response models.
                install(ContentNegotiation) {
                    json(json)
                }
                install(DefaultRequest) {
                    headers.append(HttpHeaders.Authorization, "Bearer ${config.accessToken}")
                    // Required: ContentNegotiation only serializes a request body
                    // when the request carries a Content-Type to pick the converter
                    // with; every endpoint of this client speaks JSON.
                    contentType(ContentType.Application.Json)
                }
                // new-api reports business failures as HTTP 200 with
                // success=false in the envelope; check it centrally here.
                // Non-2xx responses are deliberately left to ExpectSuccess:
                // their bodies are not uniformly the envelope (auth 401/403
                // carry one, rate limiting 429 is empty, a panic 500 uses an
                // `error` object, a reverse proxy may answer with HTML), so
                // parsing them here would only produce worse errors.
                // Response bodies are cached by default, so ContentNegotiation
                // can still deserialize the same response afterwards.
                install(
                    createClientPlugin("NewApiBusinessError") {
                        onResponse { response ->
                            if (!response.status.isSuccess()) return@onResponse // ExpectSuccess handles these

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
