package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.api.Channel
import com.hiczp.newapi.giveandtake.file.Config
import kotlin.test.*

class UserMatcherTest {
    private fun matcher(field: String, pattern: String = requireNotNull(Config.template().tracking.pattern)) =
        UserMatcher(field, pattern)

    // The unselected field contains a valid marker to detect accidental fallback or field mixing.
    private fun channel(field: String, text: String?) = Channel(
        id = 1,
        name = "channel",
        usedQuota = 0,
        tag = if (field == "tag") text else "user:99",
        remark = if (field == "remark") text else "user:99",
    )

    @Test
    fun testNullSelectedFieldIsUntracked() {
        listOf("remark", "tag").forEach { field ->
            assertNull(matcher(field).userId(channel(field, null)), field)
        }
    }

    @Test
    fun testEmptySelectedFieldIsUntracked() {
        listOf("remark", "tag").forEach { field ->
            assertNull(matcher(field).userId(channel(field, "")), field)
        }
    }

    @Test
    fun testWhitespaceOnlySelectedFieldIsUntracked() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field)
            listOf(" ", "   ", "\t", " \t ", "\n", "\r\n", " \n\t\r\n ").forEach { text ->
                assertNull(matcher.userId(channel(field, text)), "${field}: <$text>")
            }
        }
    }

    @Test
    fun testSingleLineMustMatchEntirelyWithoutTrimming() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field)
            assertEquals(1L, matcher.userId(channel(field, "user:1")), field)
            assertEquals(2147483648L, matcher.userId(channel(field, "user:2147483648")), field)
            assertEquals(Long.MAX_VALUE, matcher.userId(channel(field, "user:9223372036854775807")), field)
            listOf(
                "Other notes",
                "Notes user:1",
                "user:1 notes",
                " user:1",
                "user:1 ",
                "\tuser:1\t",
                "USER:1",
                "user:"
            ).forEach { text ->
                assertNull(matcher.userId(channel(field, text)), "${field}: <$text>")
            }
        }
    }

    @Test
    fun testMultipleLinesFindMarkerAtBeginningMiddleOrEnd() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field)
            listOf("\n", "\r\n").forEach { separator ->
                listOf(
                    "user:7${separator}Other notes",
                    "Notes${separator}user:7${separator}Other notes",
                    "Notes${separator}user:7",
                    "${separator} ${separator}user:7${separator}\t${separator}",
                ).forEach { text ->
                    assertEquals(7L, matcher.userId(channel(field, text)), "${field}: <$text>")
                }
                assertNull(matcher.userId(channel(field, "Notes${separator}Other notes")), field)
                assertNull(matcher.userId(channel(field, "user:${separator}7")), field)
            }
        }
    }

    @Test
    fun testFirstMatchingLineWins() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field)
            assertEquals(1L, matcher.userId(channel(field, "user:1\nOther notes\nuser:2")), field)
            assertEquals(2L, matcher.userId(channel(field, "Notes\r\nuser:2\r\nuser:1")), field)
        }
    }

    @Test
    fun testNamedGroupIgnoresOtherCaptures() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field, "(owner):(?<userId>[0-9]+)")
            assertEquals(7L, matcher.userId(channel(field, "owner:7")), field)
        }
    }

    @Test
    fun testInvalidFirstMatchDoesNotFallThroughToSecond() {
        listOf("remark", "tag").forEach { field ->
            val matcher = matcher(field, "user:(?<userId>[0-9]+)")
            listOf("user:0\nuser:2", "user:9223372036854775808\nuser:2").forEach { text ->
                assertFailsWith<IllegalArgumentException> { matcher.userId(channel(field, text)) }
            }
        }
    }

    @Test
    fun testNamedGroupProbePreservesAlternationAnchorsFlagsAndBackreferences() {
        listOf(
            "user:(?<userId>[0-9]+)|ignored" to "user:7",
            "(?i)^user:(?<userId>[0-9]+)$" to "USER:7",
            """^(user):(?<userId>[0-9]+):\1$""" to "user:7:user",
        ).forEach { (pattern, text) ->
            val matcher = matcher("remark", pattern)
            assertEquals(7L, matcher.userId(channel("remark", text)), pattern)
        }
    }

    @Test
    fun testNamedGroupProbeSupportsTrailingCommentsAndQuotedLiterals() {
        listOf(
            "(?x)user:(?<userId>[0-9]+) # owner",
            """user:(?<userId>[0-9]+)\Q""",
        ).forEach { pattern ->
            val matcher = matcher("remark", pattern)
            assertEquals(7L, matcher.userId(channel("remark", "user:7")), pattern)
        }
    }

    @Test
    fun testInvalidPatternOrMissingNamedGroupFailsAtConstruction() {
        listOf("[", "user:([0-9]+)", "user:(?<owner>[0-9]+)").forEach { pattern ->
            val exception = assertFailsWith<IllegalArgumentException> { matcher("remark", pattern) }
            assertNotNull(exception.cause)
        }
    }
}
