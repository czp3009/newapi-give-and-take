package com.hiczp.newapi.giveandtake.api

import de.jensklingenberg.ktorfit.Ktorfit
import io.ktor.client.*
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList

/**
 * Delegates admin endpoints to [NewApi], with pagination and nullable lookup helpers.
 *
 * The caller owns the supplied HTTP client, including its configuration and lifecycle.
 */
class NewApiClient private constructor(
    api: NewApi,
) : NewApi by api {
    /**
     * Collect channels in descending ID order, keeping only the first occurrence of each ID.
     * The last page is computed from each response's total and page size. Missing data
     * fails the call; pagination is not a transactional snapshot of concurrent server edits.
     *
     * @param pageSize Requested page size, from 1 through [MAX_PAGE_SIZE].
     * @throws IllegalArgumentException If [pageSize] is outside the accepted range.
     */
    suspend fun listAllChannels(pageSize: Int = MAX_PAGE_SIZE): List<Channel> = flow {
        require(pageSize in 1..MAX_PAGE_SIZE) { "page_size must be within 1..$MAX_PAGE_SIZE (server cap)" }
        val ids = mutableSetOf<Long>()
        var page = 1
        while (true) {
            val data = listChannels(page = page, pageSize = pageSize, idSort = true).data
                ?: throw NewApiException("Channel list response has no data")
            data.items.forEach { channel ->
                if (ids.add(channel.id)) {
                    emit(channel)
                }
            }
            val lastPage = (data.total + data.pageSize - 1) / data.pageSize
            if (page >= lastPage) {
                break
            }
            page++
        }
    }.toList()

    /** Returns null only for new-api's exact `record not found` business failure; HTTP errors and missing data propagate. */
    suspend fun findChannel(channelId: Long): Channel? = try {
        getChannel(channelId).data ?: throw NewApiException("Channel response has no data")
    } catch (exception: NewApiException) {
        if (exception.message == "record not found") null else throw exception
    }

    companion object {
        /** Maximum page size accepted by [listAllChannels], also used as its default. */
        const val MAX_PAGE_SIZE = 100

        /**
         * Bind endpoints to an existing HTTP client without modifying or closing it.
         * The supplied client must already have serialization, authentication and error handling configured.
         */
        fun create(baseUrl: String, httpClient: HttpClient): NewApiClient {
            val ktorfit = Ktorfit.Builder()
                .baseUrl("${baseUrl.trimEnd('/')}/")
                .httpClient(httpClient)
                .build()

            return NewApiClient(ktorfit.createNewApi())
        }
    }
}
