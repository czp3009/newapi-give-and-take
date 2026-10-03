package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A channel of a new-api instance.
 *
 * Only the fields exposed by this client are declared. [createHttpClient] configures deserialization to ignore other response fields.
 *
 * All declared fields are required, including the nullable [tag] and [remark].
 * A missing field fails to deserialize instead of receiving a default value.
 */
@Serializable
data class Channel(
    val id: Long,
    val name: String,
    /** Recorded consumption in integer quota units; no currency conversion is applied. */
    @SerialName("used_quota")
    val usedQuota: Long,
    val tag: String?,
    val remark: String?,
)

/** Required fields of the `data` payload returned by `GET /api/channel/`. */
@Serializable
data class ChannelListData(
    val items: List<Channel>,
    /** Total number of channels matching the filters. */
    val total: Long,
    val page: Int,
    @SerialName("page_size")
    val pageSize: Int,
    /** Channel count grouped by channel type. */
    @SerialName("type_counts")
    val typeCounts: Map<String, Long>,
)
