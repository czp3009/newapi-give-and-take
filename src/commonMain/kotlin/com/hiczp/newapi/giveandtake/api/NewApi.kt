package com.hiczp.newapi.giveandtake.api

import de.jensklingenberg.ktorfit.http.*
import kotlinx.serialization.json.JsonElement

/**
 * Endpoints of the new-api admin API used by this tool.
 *
 * Methods return response envelopes; see [createHttpClient] for error handling.
 */
interface NewApi {
    /**
     * List channels page by page (admin permission required).
     *
     * Endpoint: `GET /api/channel/`. Each item exposes the fields in [Channel].
     * Arguments are forwarded without local validation. Null optional arguments
     * are omitted from the query, leaving their behavior to the server.
     *
     * @param page Page number, starting at 1 (query parameter `p`).
     * @param pageSize Requested page size; [NewApiClient.listAllChannels] accepts 1 through 100.
     * @param status Status filter, such as `enabled` or `disabled`.
     * @param type Channel type filter.
     * @param group Channel group filter.
     * @param tagMode Tag aggregation mode: aggregate channels by tag.
     * @param idSort Sort by channel id descending (legacy parameter).
     * @param sortBy Requested sort field, such as `id` or `priority`.
     * @param sortOrder Requested sort direction: `asc` or `desc`.
     */
    @GET("api/channel/")
    suspend fun listChannels(
        @Query("p")
        page: Int,
        @Query("page_size")
        pageSize: Int,
        @Query("status")
        status: String? = null,
        @Query("type")
        type: Int? = null,
        @Query("group")
        group: String? = null,
        @Query("tag_mode")
        tagMode: Boolean? = null,
        @Query("id_sort")
        idSort: Boolean? = null,
        @Query("sort_by")
        sortBy: String? = null,
        @Query("sort_order")
        sortOrder: String? = null,
    ): ApiResponse<ChannelListData>

    /** Gets one channel by ID; a missing channel is reported as a business failure. */
    @GET("api/channel/{id}")
    suspend fun getChannel(
        @Path("id")
        id: Long,
    ): ApiResponse<Channel>

    /**
     * Perform a user management action (admin permission required).
     *
     * Endpoint: `POST /api/user/manage`. [ManageUserRequest] defaults to
     * [MANAGE_ACTION_ADD_QUOTA] with [QuotaAdjustMode.ADD]. Response `data`
     * is retained as JSON when present and is null when absent or explicitly null.
     * Do not blindly retry quota adjustments after an uncertain response.
     */
    @POST("api/user/manage")
    suspend fun manageUser(
        @Body
        request: ManageUserRequest,
    ): ApiResponse<JsonElement>
}
