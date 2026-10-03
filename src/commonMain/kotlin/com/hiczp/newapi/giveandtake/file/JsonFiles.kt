package com.hiczp.newapi.giveandtake.file

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.writeString
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.io.decodeFromSource
import kotlinx.serialization.json.io.encodeToSink

/** Streams configuration/state JSON and saves state by atomically replacing it with a complete temporary file. */
@OptIn(ExperimentalSerializationApi::class)
class JsonFiles(
    configPath: String = "./config.json",
    statePath: String = "./state.json",
) {
    private val fileSystem = SystemFileSystem
    private val json = Json {
        prettyPrint = true
        encodeDefaults = true
    }
    val configPath: Path = resolve(configPath)
    val statePath: Path = resolve(statePath)
    private val temporaryStatePath = resolve("${this.statePath}.tmp")

    init {
        require(this.configPath != this.statePath) { "Config and state must use different files" }
        require(this.configPath != temporaryStatePath) { "Config cannot use the temporary state file path" }
    }

    /** Returns true if a template config was created; an existing file is never overwritten. */
    fun createConfigIfMissing(): Boolean {
        if (fileSystem.exists(configPath)) return false
        fileSystem.sink(configPath).buffered().use {
            json.encodeToSink(Config.template(), it)
            it.writeString("\n")
        }
        return true
    }

    /** Returns true if a template state was created; an existing file is never overwritten. */
    fun createStateIfMissing(): Boolean {
        if (fileSystem.exists(statePath)) return false
        saveState(State.template())
        return true
    }

    fun readConfig(): Config = try {
        fileSystem.source(configPath).buffered().use { json.decodeFromSource<Config>(it) }
    } catch (exception: SerializationException) {
        throw IllegalArgumentException("Invalid config file format at $configPath", exception)
    }

    /** Deserializes the stored structure without additional validation of IDs or quota baselines. */
    fun readState(): State = try {
        fileSystem.source(statePath).buffered().use { json.decodeFromSource<State>(it) }
    } catch (exception: SerializationException) {
        throw IllegalArgumentException("Invalid state file format at $statePath", exception)
    }

    /** Closes the sibling temporary file before atomic replacement; failures propagate without an in-place fallback. */
    fun saveState(state: State) {
        fileSystem.sink(temporaryStatePath).buffered().use {
            json.encodeToSink(state, it)
            it.writeString("\n")
            it.flush()
        }
        fileSystem.atomicMove(temporaryStatePath, statePath)
    }

    private fun resolve(value: String): Path {
        require(value.isNotBlank()) { "File path must not be blank" }
        val path = Path(value)
        val absolute = if (path.isAbsolute) path else Path(fileSystem.resolve(Path(".")), value)
        val parent = requireNotNull(absolute.parent) { "File path must include a file name" }
        fileSystem.createDirectories(parent)
        return if (fileSystem.exists(absolute)) {
            require(fileSystem.metadataOrNull(absolute)?.isRegularFile == true) { "Expected a regular file at $absolute" }
            fileSystem.resolve(absolute)
        } else {
            Path(fileSystem.resolve(parent), absolute.name)
        }
    }
}
