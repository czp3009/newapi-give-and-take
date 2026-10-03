package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.api.*
import com.hiczp.newapi.giveandtake.file.JsonFiles
import com.hiczp.newapi.giveandtake.file.State
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.http.*
import kotlinx.coroutines.CancellationException

private val logger = KotlinLogging.logger("com.hiczp.newapi.giveandtake")

/** Runs one settlement pass with the application's fixed HTTP configuration. */
class Application {
    /** Applies already-resolved overrides to file settings; once the HTTP client exists, partial progress is saved in finally. */
    suspend fun run(
        configPath: String,
        statePath: String,
        baseUrl: String? = null,
        accessToken: String? = null,
        field: String? = null,
        pattern: String? = null,
    ) {
        val jsonFiles = JsonFiles(configPath, statePath)
        jsonFiles.createConfigIfMissing()
        jsonFiles.createStateIfMissing()

        val config = jsonFiles.readConfig()
        val state = jsonFiles.readState()
        val baseUrl = configurationValue("newApi.baseUrl", baseUrl, config.newApi.baseUrl)
        val accessToken = configurationValue("newApi.accessToken", accessToken, config.newApi.accessToken)
        val field = configurationValue("tracking.field", field, config.tracking.field)
        val pattern = configurationValue("tracking.pattern", pattern, config.tracking.pattern)
        val userMatcher = UserMatcher(field, pattern)

        val proxyUrl =
            getEnvironmentVariable(if (Url(baseUrl).protocol == URLProtocol.HTTPS) "HTTPS_PROXY" else "HTTP_PROXY")
        val httpClient = createHttpClient(accessToken, proxyUrl = proxyUrl)
        try {
            val newApiClient = NewApiClient.create(baseUrl, httpClient)
            logger.info { "Fetching channels" }
            val channels = newApiClient.listAllChannels().mapNotNull { channel ->
                runCatching {
                    userMatcher.userId(channel)?.let { userId -> channel to userId }
                }.onFailure { exception ->
                    logger.error(exception) { "Channel ${channel.id}: failed to match user" }
                }.getOrNull()
            }
            val channelIds = channels.map { (channel, _) -> channel.id }.toSet()
            logger.info { "Fetched ${channelIds.size} channels associated with users" }
            logger.info { "Processing" }
            // Pagination can omit an existing channel, so absence from the filtered list alone does not confirm removal.
            state.channels.keys.filter { it !in channelIds }.forEach { channelId ->
                processMissingChannel(channelId, userMatcher, newApiClient, state)
            }
            channels.forEach { (channel, userId) ->
                processChannel(channel, userId, newApiClient, state)
            }
        } finally {
            httpClient.close()
            jsonFiles.saveState(state)
        }
        logger.info { "Done" }
    }

    /** Rechecks a stored channel absent from the filtered list; lookup failures retain it, while absent/invalid matches remove it. */
    private suspend fun processMissingChannel(
        channelId: Long,
        userMatcher: UserMatcher,
        newApiClient: NewApiClient,
        state: State
    ) {
        try {
            val channel = newApiClient.findChannel(channelId)
            val userId = runCatching {
                channel?.let { userMatcher.userId(it) }
            }.onFailure { exception ->
                logger.error(exception) { "Channel $channelId: failed to match user" }
            }.getOrNull()
            if (channel == null || userId == null) {
                state.channels.remove(channelId)
                logger.info { "Channel $channelId: confirmed removed channel or user association" }
            } else {
                logger.info { "Channel $channelId: confirmed user association for user $userId; retained channel" }
                processChannel(channel, userId, newApiClient, state)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Channel $channelId: failed to check removal; retained baseline" }
        }
    }

    /** New or changed associations start fresh; ordinary failures are logged, but cancellation propagates to the caller. */
    internal suspend fun processChannel(channel: Channel, userId: Long, newApiClient: NewApiClient, state: State) {
        try {
            val stateChannel = state.channels[channel.id]
            if (stateChannel == null) {
                state.channels[channel.id] = State.Channel(userId, channel.usedQuota)
                logger.info { "Channel ${channel.id}: initialized baseline at ${channel.usedQuota} for user $userId" }
            } else if (stateChannel.userId != userId) {
                state.channels[channel.id] = State.Channel(userId, channel.usedQuota)
                logger.info { "Channel ${channel.id}: user changed from ${stateChannel.userId} to $userId; reset baseline to ${channel.usedQuota} without reward" }
            } else {
                processReward(channel, userId, newApiClient, state)
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            logger.error(exception) { "Channel ${channel.id}: processing failed" }
        }
    }

    /** Updates the consumption baseline only after a successful quota adjustment. */
    private suspend fun processReward(channel: Channel, userId: Long, newApiClient: NewApiClient, state: State) {
        val stateChannel = state.channels.getValue(channel.id)
        val delta = channel.usedQuota - stateChannel.usedQuota
        if (delta == 0L) {
            logger.info { "Channel ${channel.id}: no quota change for user $userId" }
            return
        }
        newApiClient.manageUser(
            ManageUserRequest(
                id = userId,
                mode = if (delta > 0) QuotaAdjustMode.ADD else QuotaAdjustMode.SUBTRACT,
                value = if (delta > 0) delta else -delta,
            ),
        )
        stateChannel.usedQuota = channel.usedQuota
        logger.info { "Channel ${channel.id}: adjusted user $userId quota by $delta; baseline is ${channel.usedQuota}" }
    }
}

internal fun configurationValue(name: String, vararg values: String?): String =
    requireNotNull(values.firstOrNull { !it.isNullOrEmpty() }) { "Missing configuration: $name" }
