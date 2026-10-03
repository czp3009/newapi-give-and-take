package com.hiczp.newapi.giveandtake.file

import kotlinx.serialization.Serializable

/** Per-channel user associations and consumption baselines retained between settlement passes. */
@Serializable
data class State(
    val channels: MutableMap<Long, Channel>,
) {
    /** A new association starts at current consumption; subsequent successful adjustments update [usedQuota]. */
    @Serializable
    data class Channel(
        val userId: Long,
        var usedQuota: Long,
    )

    companion object {
        fun template(): State = State(channels = mutableMapOf())
    }
}
