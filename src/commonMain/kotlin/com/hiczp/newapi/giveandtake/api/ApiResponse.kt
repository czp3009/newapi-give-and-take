package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.Serializable

/**
 * Common response envelope of the new-api admin API.
 *
 * [success] and [message] are required. [data] may be omitted or explicitly null.
 */
@Serializable
data class ApiResponse<T>(
    val success: Boolean,
    val message: String,
    val data: T? = null,
)
