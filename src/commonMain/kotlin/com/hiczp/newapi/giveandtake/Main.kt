package com.hiczp.newapi.giveandtake

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.default
import kotlinx.coroutines.runBlocking

/** Runs one settlement pass; scheduling is handled by the caller. */
fun main(args: Array<String>) {
    val argParser = ArgParser("newapi-give-and-take")
    val configPath by argParser.option(
        ArgType.String,
        fullName = "config",
        shortName = "c",
        description = "Config file"
    )
        .default("./config.json")
    val statePath by argParser.option(ArgType.String, fullName = "state", shortName = "s", description = "State file")
        .default("./state.json")
    val baseUrl by argParser.option(
        ArgType.String,
        fullName = "newApi.baseUrl",
        description = "new-api URL (env: NEW_API_BASE_URL)"
    )
    val accessToken by argParser.option(
        ArgType.String,
        fullName = "newApi.accessToken",
        description = "Admin access token (env: NEW_API_ACCESS_TOKEN)"
    )
    val field by argParser.option(
        ArgType.String,
        fullName = "tracking.field",
        description = "Tracking field: remark or tag (env: TRACKING_FIELD)"
    )
    val pattern by argParser.option(
        ArgType.String,
        fullName = "tracking.pattern",
        description = "Tracking regex with a named userId group (env: TRACKING_PATTERN)"
    )
    argParser.parse(args)
    runBlocking {
        Application().run(
            configPath,
            statePath,
            baseUrl = baseUrl?.takeIf { it.isNotEmpty() } ?: getEnvironmentVariable("NEW_API_BASE_URL"),
            accessToken = accessToken?.takeIf { it.isNotEmpty() } ?: getEnvironmentVariable("NEW_API_ACCESS_TOKEN"),
            field = field?.takeIf { it.isNotEmpty() } ?: getEnvironmentVariable("TRACKING_FIELD"),
            pattern = pattern?.takeIf { it.isNotEmpty() } ?: getEnvironmentVariable("TRACKING_PATTERN"),
        )
    }
}
