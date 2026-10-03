package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.file.Config
import kotlinx.coroutines.test.runTest
import kotlinx.io.files.SystemFileSystem
import kotlinx.serialization.json.Json
import kotlin.test.*

class ApplicationTest : FileTest() {
    @Test
    fun testFirstRunCreatesDefaults() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("config.json")
        val statePath = directory.path("state.json")
        val exception =
            assertFailsWith<IllegalArgumentException> { Application().run(configPath.toString(), statePath.toString()) }
        assertEquals("Missing configuration: newApi.baseUrl", exception.message)
        assertEquals(Json.parseToJsonElement("""{"channels":{}}"""), Json.parseToJsonElement(readText(statePath)))
        val config = Json.decodeFromString<Config>(readText(configPath))
        assertEquals("", config.newApi.baseUrl)
        assertEquals("", config.newApi.accessToken)
        assertEquals(Config.template().tracking, config.tracking)
    }

    @Test
    fun testCreatesCustomPathsAndParentsWithoutOverwritingExistingState() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("custom/config/settings.json")
        val statePath = directory.path("custom/data/baselines.json")
        assertFailsWith<IllegalArgumentException> { Application().run(configPath.toString(), statePath.toString()) }
        assertTrue(SystemFileSystem.exists(configPath))
        assertEquals(Json.parseToJsonElement("""{"channels":{}}"""), Json.parseToJsonElement(readText(statePath)))
        writeText(statePath, """{"channels":{"42":{"userId":1,"usedQuota":500}}}""")
        SystemFileSystem.delete(configPath)
        assertFailsWith<IllegalArgumentException> { Application().run(configPath.toString(), statePath.toString()) }
        assertEquals("""{"channels":{"42":{"userId":1,"usedQuota":500}}}""", readText(statePath))
    }

    @Test
    fun testCorruptConfigFailsWithoutOverwritingIt() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("config.json")
        val statePath = directory.path("state.json")
        writeText(configPath, "not json")
        assertFailsWith<IllegalArgumentException> { Application().run(configPath.toString(), statePath.toString()) }
        assertEquals("not json", readText(configPath))
    }

    @Test
    fun testMissingAndExistingTemplateFollowSameResolution() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("config.json")
        val statePath = directory.path("state.json")
        repeat(2) {
            val exception = assertFailsWith<IllegalArgumentException> {
                Application().run(
                    configPath.toString(),
                    statePath.toString(),
                    baseUrl = "http://newapi.test",
                    accessToken = "placeholder",
                    pattern = "["
                )
            }
            assertEquals("tracking.pattern must be a valid regex with a named userId group", exception.message)
        }
        val config = Json.decodeFromString<Config>(readText(configPath))
        assertEquals("", config.newApi.baseUrl)
        assertEquals(Config.template().tracking, config.tracking)
    }

    @Test
    fun testPartialConfigReportsOnlySettingMissingAfterOverrides() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("config.json")
        val statePath = directory.path("state.json")
        writeText(configPath, """{"newApi":{"baseUrl":"http://newapi.test"},"tracking":{"field":"remark"}}""")
        val exception = assertFailsWith<IllegalArgumentException> {
            Application().run(
                configPath.toString(),
                statePath.toString(),
                baseUrl = "",
                accessToken = "placeholder",
                field = "",
                pattern = ""
            )
        }
        assertEquals("Missing configuration: tracking.pattern", exception.message)
    }

    @Test
    fun testEmptyOverrideUsesFilePattern() = runTest {
        val directory = testDirectory()
        val configPath = directory.path("config.json")
        val statePath = directory.path("state.json")
        writeText(
            configPath,
            """{"newApi":{"baseUrl":"http://newapi.test","accessToken":"placeholder"},"tracking":{"field":"remark","pattern":"["}}"""
        )
        val exception = assertFailsWith<IllegalArgumentException> {
            Application().run(configPath.toString(), statePath.toString(), pattern = "")
        }
        assertEquals("tracking.pattern must be a valid regex with a named userId group", exception.message)
    }

    @Test
    fun testSameFilePathsAreRejectedBeforeWriting() = runTest {
        val directory = testDirectory()
        val config = directory.path("config.json")
        assertFailsWith<IllegalArgumentException> {
            Application().run(
                config.toString(),
                directory.path("./config.json").toString()
            )
        }
        assertFalse(SystemFileSystem.exists(config))
    }
}
