package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.file.Config
import com.hiczp.newapi.giveandtake.file.JsonFiles
import com.hiczp.newapi.giveandtake.file.State
import kotlinx.io.IOException
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.*

class JsonFilesTest : FileTest() {
    @Test
    fun testReadFailuresPreserveSerializationCause() {
        val directory = testDirectory()
        val files = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        writeText(files.configPath, "not json")
        writeText(files.statePath, "not json")
        val configException = assertFailsWith<IllegalArgumentException> { files.readConfig() }
        val stateException = assertFailsWith<IllegalArgumentException> { files.readState() }
        assertIs<SerializationException>(configException.cause)
        assertIs<SerializationException>(stateException.cause)
    }

    @Test
    fun testLargeStateRoundTripAndInPlaceUpdates() {
        val directory = testDirectory()
        val files = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        val state = State.template()
        repeat(20_000) { index ->
            state.channels[Int.MAX_VALUE.toLong() + index + 1] =
                State.Channel(Long.MAX_VALUE - index, Long.MAX_VALUE - index)
        }
        files.saveState(state)
        val restored = files.readState()
        assertEquals(state, restored)
        restored.channels[Long.MAX_VALUE] = State.Channel(Long.MAX_VALUE, 123)
        files.saveState(restored)
        assertEquals(restored, files.readState())
        assertFalse(SystemFileSystem.exists(directory.path("state.json.tmp")))
    }

    @Test
    fun testConfigReadsUtf8AndEscapesAcrossBuffers() {
        val directory = testDirectory()
        val files = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        val pattern = "${"\u5907\u6ce8\uD83D\uDE42".repeat(4096)}(?<userId>\\d+)"
        val config = Config(Config.template().newApi, Config.Tracking("remark", pattern))
        writeText(files.configPath, Json.encodeToString(config))
        assertEquals(config.tracking, files.readConfig().tracking)
    }

    @Test
    fun testRealFileSystemReplacesExistingState() {
        val directory = testDirectory()
        val files = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        assertTrue(files.createConfigIfMissing())
        assertFalse(SystemFileSystem.exists(files.statePath))
        assertTrue(files.createStateIfMissing())
        files.saveState(State(mutableMapOf(42L to State.Channel(1, 100L))))
        files.saveState(State(mutableMapOf(42L to State.Channel(1, 150L), 57L to State.Channel(1, Long.MAX_VALUE))))
        assertEquals(
            Json.parseToJsonElement("""{"channels":{"42":{"userId":1,"usedQuota":150},"57":{"userId":1,"usedQuota":9223372036854775807}}}"""),
            Json.parseToJsonElement(readText(files.statePath))
        )
        assertEquals(
            mapOf(42L to State.Channel(1, 150L), 57L to State.Channel(1, Long.MAX_VALUE)),
            files.readState().channels
        )
        assertFalse(files.createConfigIfMissing())
        assertFalse(files.createStateIfMissing())
        assertEquals(
            mapOf(42L to State.Channel(1, 150L), 57L to State.Channel(1, Long.MAX_VALUE)),
            files.readState().channels
        )
        assertFalse(SystemFileSystem.exists(directory.path("state.json.tmp")))
    }

    @Test
    fun testPartialTemporaryFileIsIgnoredAndReplacedOnNextSave() {
        val directory = testDirectory()
        val state = directory.path("state.json")
        val temporary = directory.path("state.json.tmp")
        val config = directory.path("config.json")
        val files = JsonFiles(config.toString(), state.toString())
        files.createStateIfMissing()
        files.saveState(State(mutableMapOf(42L to State.Channel(1, 100L))))
        val previous = readText(state)
        // Simulate a process stopping after writing only part of the temporary file.
        writeText(temporary, """{"channels":{"42":""")
        val restarted = JsonFiles(config.toString(), state.toString())
        assertFalse(restarted.createStateIfMissing())
        assertEquals(previous, readText(state))
        assertEquals(mapOf(42L to State.Channel(1, 100L)), restarted.readState().channels)
        restarted.saveState(State(mutableMapOf(42L to State.Channel(1, 200L))))
        assertEquals(mapOf(42L to State.Channel(1, 200L)), restarted.readState().channels)
        assertFalse(SystemFileSystem.exists(temporary))
    }

    @Test
    fun testTemporaryWriteFailurePreservesOriginalState() {
        val directory = testDirectory()
        val files = JsonFiles(directory.path("config.json").toString(), directory.path("state.json").toString())
        assertTrue(files.createStateIfMissing())
        assertFalse(SystemFileSystem.exists(files.configPath))
        files.saveState(State(mutableMapOf(42L to State.Channel(1, 100L))))
        SystemFileSystem.createDirectories(directory.path("state.json.tmp"))
        assertFailsWith<IOException> { files.saveState(State(mutableMapOf(42L to State.Channel(1, 200L)))) }
        assertEquals(mapOf(42L to State.Channel(1, 100L)), files.readState().channels)
    }

    @Test
    fun testReplacementFailureDoesNotFallBackToOverwritingDestination() {
        val directory = testDirectory()
        val targetDirectory = directory.path("state.json")
        val files = JsonFiles(directory.path("config.json").toString(), targetDirectory.toString())
        SystemFileSystem.createDirectories(targetDirectory)
        val marker = directory.path("state.json/keep.txt")
        writeText(marker, "keep")
        assertFailsWith<IOException> { files.saveState(State.template()) }
        assertTrue(SystemFileSystem.exists(targetDirectory))
        assertEquals("keep", readText(marker))
        assertEquals(
            Json.parseToJsonElement("""{"channels":{}}"""),
            Json.parseToJsonElement(readText(directory.path("state.json.tmp")))
        )
    }

    @Test
    fun testConfigCannotUseTemporaryStatePath() {
        val directory = testDirectory()
        val config = directory.path("state.json.tmp")
        writeText(config, "config must survive")
        assertFailsWith<IllegalArgumentException> {
            JsonFiles(config.toString(), directory.path("state.json").toString())
        }
        assertEquals("config must survive", readText(config))
    }
}
