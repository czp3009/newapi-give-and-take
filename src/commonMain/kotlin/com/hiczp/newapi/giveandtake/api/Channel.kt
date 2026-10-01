package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A channel of a new-api instance.
 *
 * Only the fields this project reads are declared; the server sends the full
 * detail of new-api's `model.Channel`, and every field not declared here is
 * ignored via `ignoreUnknownKeys` (the server also never returns the key
 * field, Go: `Omit("key")`).
 *
 * Types follow the Go source: `id` / `name` / `used_quota` are Go value types,
 * so they are required and non-null; `tag` / `remark` are Go pointer types,
 * serialized as `null` when unset, so they are nullable. A required field
 * missing from a response is a schema mismatch and fails to deserialize
 * instead of silently falling back to a made-up default.
 */
@Serializable
data class Channel(
    val id: Int,
    val name: String,
    /** Used quota, in new-api's integer quota unit (500000 = $1). */
    @SerialName("used_quota")
    val usedQuota: Long,
    /** Tag, used by new-api to group channels; can identify a contributor. */
    val tag: String?,
    /** Remark. */
    val remark: String?,
)

/** The `data` part of the `GET api/channel/` response (see new-api `controller/channel.go` -> `GetAllChannels`). */
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
