package com.hiczp.newapi.giveandtake.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Request body of `POST api/user/manage`. */
@Serializable
data class ManageUserRequest(
    /** Target user id. */
    val id: Int,
    /** Manage action; defaults to "add_quota" ([MANAGE_ACTION_ADD_QUOTA]) for quota adjustments. */
    val action: String = MANAGE_ACTION_ADD_QUOTA,
    /** How the quota value is applied. */
    val mode: QuotaAdjustMode = QuotaAdjustMode.ADD,
    /** Adjustment value; must be positive for add/subtract. */
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
