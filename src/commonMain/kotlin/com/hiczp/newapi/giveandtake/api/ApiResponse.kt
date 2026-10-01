package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.Serializable

/**
 * Common response envelope of the new-api admin API.
 *
 * Built by new-api's `common.ApiSuccess` / `ApiError` helpers: `success` and
 * `message` are always present, while responses without a payload (including
 * every business failure) omit `data`, hence the nullable data part.
 *
 * Business failures are reported with HTTP 200 and `success=false`, so a
 * response interceptor turns them into [NewApiException];
 * [NewApiClient.listAllChannels] unwraps [data].
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
)
