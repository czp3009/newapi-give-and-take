package com.hiczp.newapi.giveandtake

import com.hiczp.newapi.giveandtake.api.Channel

/** Finds a channel's user by matching complete lines in source order; the first match wins. */
class UserMatcher(private val field: String, pattern: String) {
    init {
        require(field == "remark" || field == "tag") { "tracking.field must be remark or tag" }
    }

    private val regex = try {
        Regex(pattern).also {
            // A leading empty alternative allows group inspection without appending syntax that trailing comments or \Q could consume.
            Regex("|$pattern").find("")!!.groups["userId"]
        }
    } catch (exception: Exception) {
        throw IllegalArgumentException("tracking.pattern must be a valid regex with a named userId group", exception)
    }

    /** Null means untracked; a matching line with an invalid user ID throws instead. */
    fun userId(channel: Channel): Long? {
        val text = (if (field == "remark") channel.remark else channel.tag) ?: return null
        text.lineSequence().forEach { line ->
            val matchResult = regex.matchEntire(line) ?: return@forEach
            val id = matchResult.groups["userId"]?.value?.toLongOrNull()
            require(id != null && id > 0) { "Matched userId must be a positive integer" }
            return id
        }
        return null
    }
}
