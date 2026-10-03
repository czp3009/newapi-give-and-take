package com.hiczp.newapi.giveandtake

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.wcstr
import platform.windows.SetEnvironmentVariableW
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalForeignApi::class)
class EnvironmentTest {
    @Test
    fun testUnicodeNameAndValue() {
        withEnvironmentVariable("用户:(?<userId>[1-9][0-9]*)😀") { name, value ->
            assertEquals(value, getEnvironmentVariable(name))
        }
    }

    @Test
    fun testLongValue() {
        withEnvironmentVariable("中文😀".repeat(1024)) { name, value ->
            assertEquals(value, getEnvironmentVariable(name))
        }
    }

    @Test
    fun testEmptyAndMissingValuesAreUnconfigured() {
        withEnvironmentVariable("") { name, _ ->
            assertNull(getEnvironmentVariable(name))
        }
        withEnvironmentVariable(null) { name, _ ->
            assertNull(getEnvironmentVariable(name))
        }
    }

    private fun withEnvironmentVariable(value: String?, block: (String, String?) -> Unit) = memScoped {
        val name = "NEWAPI_GIVE_AND_TAKE_TEST_中文_${Random.nextLong().toULong()}"
        check(SetEnvironmentVariableW(name.wcstr.ptr, value?.wcstr?.ptr) != 0)
        try {
            block(name, value)
        } finally {
            check(SetEnvironmentVariableW(name.wcstr.ptr, null) != 0)
        }
    }
}
