package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body of `POST /api/user/manage`; values are sent without local validation. */
@Serializable
data class ManageUserRequest(
    val id: Long,
    val action: String = MANAGE_ACTION_ADD_QUOTA,
    val mode: QuotaAdjustMode = QuotaAdjustMode.ADD,
    /** Adjustment value in integer quota units; validity is checked by the server. */
    val value: Long,
)

/** How a user quota adjustment is applied. */
@Serializable
enum class QuotaAdjustMode {
    /** Add to the current quota. */
    @SerialName("add")
    ADD,

    /** Subtract from the current quota. */
    @SerialName("subtract")
    SUBTRACT,

    /** Overwrite the quota with the given value. */
    @SerialName("override")
    OVERRIDE,
}

const val MANAGE_ACTION_ADD_QUOTA = "add_quota"
