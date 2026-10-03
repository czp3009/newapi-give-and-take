package com.hiczp.newapi.giveandtake.file

import kotlinx.serialization.Serializable

/** File settings used after command-line/environment overrides; omitted values remain unconfigured. */
@Serializable
class Config(
    val newApi: NewApi = NewApi(),
    val tracking: Tracking = Tracking(),
) {
    @Serializable
    class NewApi(
        val baseUrl: String? = null,
        val accessToken: String? = null,
    )

    @Serializable
    data class Tracking(
        val field: String? = null,
        val pattern: String? = null,
    )

    companion object {
        /** First-run file contents; connection settings must still be supplied through a configuration source. */
        fun template(): Config = Config(
            newApi = NewApi("", ""),
            tracking = Tracking("remark", "user:(?<userId>[1-9][0-9]*)"),
        )
    }
}
