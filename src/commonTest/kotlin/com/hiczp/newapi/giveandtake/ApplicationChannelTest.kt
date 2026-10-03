package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.api.*
import com.hiczp.newapi.giveandtake.file.JsonFiles
import com.hiczp.newapi.giveandtake.file.State
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ApplicationChannelTest : FileTest() {
    private inner class Fixture {
        val directory = testDirectory()
        val jsonFiles = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        val state = State.template()
        val adjustments = mutableListOf<ManageUserRequest>()
        var reject = false
        var failure: Exception? = null

        init {
            jsonFiles.createStateIfMissing()
        }

        suspend fun process(channel: Channel, userId: Long = 1) {
            val httpClient = mockNewApiHttpClient("placeholder-token") { request ->
                assertEquals(HttpMethod.Post, request.method)
                adjustments += Json.decodeFromString<ManageUserRequest>(request.bodyAsText())
                failure?.let { throw it }
                if (reject) respondJson(failureResponse("denied")) else respondJson(successResponse())
            }
            try {
                val newApiClient = NewApiClient.create("https://newapi.test", httpClient)
                Application().processChannel(channel, userId, newApiClient, state)
            } finally {
                httpClient.close()
            }
        }

        fun channel(id: Long = 42, quota: Long = 100) = Channel(id, "channel-$id", quota, null, "user:1")
    }

    @Test
    fun testBaselinesThenAddsSubtractsAndSkipsUnchangedConsumption() = runTest {
        val fixture = Fixture()
        fixture.process(fixture.channel(quota = 100))
        assertEquals(mapOf(42L to State.Channel(1, 100)), fixture.state.channels)
        assertEquals(0, fixture.adjustments.size)
        fixture.process(fixture.channel(quota = 150))
        assertEquals(ManageUserRequest(1, value = 50), fixture.adjustments.single())
        assertEquals(mapOf(42L to State.Channel(1, 150)), fixture.state.channels)
        fixture.process(fixture.channel(quota = 130))
        assertEquals(ManageUserRequest(1, mode = QuotaAdjustMode.SUBTRACT, value = 20), fixture.adjustments.last())
        assertEquals(mapOf(42L to State.Channel(1, 130)), fixture.state.channels)
        fixture.process(fixture.channel(quota = 130))
        assertEquals(2, fixture.adjustments.size)
    }

    @Test
    fun testChangedOwnerResetsBaselineWithoutRewardThenReceivesFutureDifference() = runTest {
        val fixture = Fixture()
        fixture.state.channels[42] = State.Channel(1, 100)
        fixture.process(fixture.channel(quota = 125), userId = 2)
        assertEquals(mapOf(42L to State.Channel(2, 125)), fixture.state.channels)
        assertEquals(0, fixture.adjustments.size)
        fixture.process(fixture.channel(quota = 150), userId = 2)
        assertEquals(ManageUserRequest(2, value = 25), fixture.adjustments.single())
        assertEquals(mapOf(42L to State.Channel(2, 150)), fixture.state.channels)
    }

    @Test
    fun testFailurePreservesBaselineForNextRun() = runTest {
        val fixture = Fixture()
        fixture.state.channels[42] = State.Channel(1, 100)
        fixture.reject = true
        fixture.process(fixture.channel(quota = 150))
        assertEquals(mapOf(42L to State.Channel(1, 100)), fixture.state.channels)
        assertEquals(1, fixture.adjustments.size)
        fixture.jsonFiles.saveState(fixture.state)
        assertEquals(fixture.state, fixture.jsonFiles.readState())
        fixture.reject = false
        fixture.process(fixture.channel(quota = 150))
        assertEquals(mapOf(42L to State.Channel(1, 150)), fixture.state.channels)
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(1, value = 50)), fixture.adjustments)
    }

    @Test
    fun testFailedChannelDoesNotPreventNextChannelFromProcessing() = runTest {
        val fixture = Fixture()
        fixture.state.channels.putAll(mapOf(42L to State.Channel(1, 100), 57L to State.Channel(2, 200)))
        fixture.reject = true
        fixture.process(fixture.channel(42, 150))
        fixture.reject = false
        fixture.process(fixture.channel(57, 230), userId = 2)
        assertEquals(mapOf(42L to State.Channel(1, 100), 57L to State.Channel(2, 230)), fixture.state.channels)
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(2, value = 30)), fixture.adjustments)
    }

    @Test
    fun testTransportFailureDoesNotRetryOrChangeBaseline() = runTest {
        val fixture = Fixture()
        fixture.state.channels[42] = State.Channel(1, 100)
        fixture.failure = IOException("uncertain response")
        fixture.process(fixture.channel(quota = 150))
        assertEquals(mapOf(42L to State.Channel(1, 100)), fixture.state.channels)
        assertEquals(1, fixture.adjustments.size)
    }

    @Test
    fun testCancellationPropagatesWithoutAdvancingState() = runTest {
        val fixture = Fixture()
        fixture.state.channels[42] = State.Channel(1, 100)
        fixture.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { fixture.process(fixture.channel(quota = 150)) }
        assertEquals(mapOf(42L to State.Channel(1, 100)), fixture.state.channels)
        assertEquals(1, fixture.adjustments.size)
    }

    @Test
    fun testCancellationPreservesEarlierSuccessfulChannelChanges() = runTest {
        val fixture = Fixture()
        fixture.state.channels.putAll(mapOf(42L to State.Channel(1, 100), 57L to State.Channel(2, 200)))
        fixture.process(fixture.channel(42, 150))
        fixture.failure = CancellationException("cancelled")
        assertFailsWith<CancellationException> { fixture.process(fixture.channel(57, 230), userId = 2) }
        assertEquals(mapOf(42L to State.Channel(1, 150), 57L to State.Channel(2, 200)), fixture.state.channels)
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(2, value = 30)), fixture.adjustments)
    }

    @Test
    fun testLargeChannelIdUserIdAndQuotaStayExact() = runTest {
        val fixture = Fixture()
        fixture.state.channels[Long.MAX_VALUE] = State.Channel(Long.MAX_VALUE, Long.MAX_VALUE - 5)
        fixture.process(fixture.channel(id = Long.MAX_VALUE, quota = Long.MAX_VALUE), userId = Long.MAX_VALUE)
        assertEquals(ManageUserRequest(Long.MAX_VALUE, value = 5), fixture.adjustments.single())
        assertEquals(mapOf(Long.MAX_VALUE to State.Channel(Long.MAX_VALUE, Long.MAX_VALUE)), fixture.state.channels)
    }

    @Test
    fun testLargeChannelIdsKeepSeparateBaselines() = runTest {
        val fixture = Fixture()
        val largeId = 4294967297L
        fixture.process(fixture.channel(largeId, 100))
        fixture.process(fixture.channel(1, 200), userId = 2)
        fixture.process(fixture.channel(largeId, 150))
        fixture.process(fixture.channel(1, 230), userId = 2)
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(2, value = 30)), fixture.adjustments)
        assertEquals(mapOf(largeId to State.Channel(1, 150), 1L to State.Channel(2, 230)), fixture.state.channels)
    }

    @Test
    fun testChannelProcessingOnlyUpdatesMemoryUntilFinalSave() = runTest {
        val fixture = Fixture()
        fixture.state.channels.putAll(mapOf(42L to State.Channel(1, 100), 57L to State.Channel(2, 200)))
        fixture.jsonFiles.saveState(fixture.state)
        val previousFile = readText(fixture.jsonFiles.statePath)
        fixture.process(fixture.channel(42, 150))
        fixture.process(fixture.channel(57, 230), userId = 2)
        fixture.process(fixture.channel(99, 300), userId = 3)
        fixture.process(fixture.channel(99, 350), userId = 4)
        assertEquals(previousFile, readText(fixture.jsonFiles.statePath))
        assertEquals(
            mapOf(42L to State.Channel(1, 150), 57L to State.Channel(2, 230), 99L to State.Channel(4, 350)),
            fixture.state.channels
        )
        fixture.jsonFiles.saveState(fixture.state)
        assertEquals(fixture.state, fixture.jsonFiles.readState())
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(2, value = 30)), fixture.adjustments)
    }

    @Test
    fun testFinalWriteFailureOccursAfterChannelProcessing() = runTest {
        val fixture = Fixture()
        fixture.state.channels.putAll(mapOf(42L to State.Channel(1, 100), 57L to State.Channel(2, 200)))
        fixture.jsonFiles.saveState(fixture.state)
        val previousFile = readText(fixture.jsonFiles.statePath)
        SystemFileSystem.createDirectories(fixture.directory.path("state.json.tmp"))
        fixture.process(fixture.channel(42, 150))
        fixture.process(fixture.channel(57, 230), userId = 2)
        assertEquals(listOf(ManageUserRequest(1, value = 50), ManageUserRequest(2, value = 30)), fixture.adjustments)
        assertFailsWith<IOException> { fixture.jsonFiles.saveState(fixture.state) }
        assertEquals(previousFile, readText(fixture.jsonFiles.statePath))
    }
}
