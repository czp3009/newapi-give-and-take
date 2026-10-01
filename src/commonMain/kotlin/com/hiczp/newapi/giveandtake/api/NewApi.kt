package com.hiczp.newapi.giveandtake.api

import de.jensklingenberg.ktorfit.http.Body
import de.jensklingenberg.ktorfit.http.GET
import de.jensklingenberg.ktorfit.http.POST
import de.jensklingenberg.ktorfit.http.Query
import kotlinx.serialization.json.JsonElement

/**
 * Endpoints of the new-api admin API used by this tool.
 *
 * Methods return the raw response envelope and rely on the official kotlinx
 * serialization converter of the HTTP client (see [NewApiClient.create]).
 * Business failures (`success=false`) throw [NewApiException] through a
 * response interceptor; [NewApiClient.listAllChannels] unwraps the `data`
 * part.
 */
interface NewApi {
    /**
     * List channels page by page (admin permission required).
     *
     * Endpoint: `GET /api/channel/` (see new-api `controller/channel.go` ->
     * `GetAllChannels`). Every item is the full channel detail (including tag /
     * remark / used_quota); the server never returns the key field. The server
     * caps `page_size` at 100.
     *
     * @param page Page number, starting at 1 (query parameter `p`).
     * @param pageSize Page size, max 100.
     * @param status Status filter: "enabled" / "disabled"; null for all.
     * @param type Channel type filter; null for all.
     * @param group Channel group filter; null for all.
     * @param tagMode Tag aggregation mode: aggregate channels by tag.
     * @param idSort Sort by channel id descending (legacy parameter).
     * @param sortBy Sort field: id / name / priority / balance / response_time / test_time.
     * @param sortOrder Sort direction: asc / desc.
     */
    @GET("api/channel/")
    suspend fun listChannels(
        @Query("p") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("status") status: String? = null,
        @Query("type") type: Int? = null,
        @Query("group") group: String? = null,
        @Query("tag_mode") tagMode: Boolean? = null,
        @Query("id_sort") idSort: Boolean? = null,
        @Query("sort_by") sortBy: String? = null,
        @Query("sort_order") sortOrder: String? = null,
    ): ApiResponse<ChannelListData>

    /**
     * Perform a user management action (admin permission required).
     *
     * Endpoint: `POST /api/user/manage` (see new-api `controller/user.go` ->
     * `ManageUser`). To adjust quota the body is
     * `{"id": <userId>, "action": "add_quota", "mode": "add", "value": <quota>}`.
     * On business success the response carries no data, so `data` stays null.
     */
    @POST("api/user/manage")
    suspend fun manageUser(@Body request: ManageUserRequest): ApiResponse<JsonElement>
}
