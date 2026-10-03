package com.hiczp.newapi.giveandtake

import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readString
import kotlinx.io.writeString
import kotlin.random.Random
import kotlin.test.AfterTest

/** Isolates each fixture under build/ without changing the process working directory. */
abstract class FileTest {
    private val directories = mutableListOf<TestDirectory>()

    protected fun testDirectory(): TestDirectory = TestDirectory().also { directories += it }

    @AfterTest
    fun removeTestDirectories() {
        directories.forEach { it.close() }
        directories.clear()
    }
}

class TestDirectory : AutoCloseable {
    private val buildDirectory = Path("build").also { SystemFileSystem.createDirectories(it) }
        .let { SystemFileSystem.resolve(it) }
    val root = Path(buildDirectory, "file-tests-${Random.nextLong().toULong()}")

    init {
        SystemFileSystem.createDirectories(root, mustCreate = true)
    }

    fun path(relative: String): Path = Path(root, relative)

    override fun close() {
        check(root.parent == buildDirectory)
        remove(root)
    }

    private fun remove(path: Path) {
        if (SystemFileSystem.metadataOrNull(path)?.isDirectory == true) {
            SystemFileSystem.list(path).forEach { remove(it) }
        }
        SystemFileSystem.delete(path, mustExist = false)
    }
}

fun readText(path: Path): String = SystemFileSystem.source(path).buffered().use { it.readString() }

fun writeText(path: Path, text: String) {
    SystemFileSystem.sink(path).buffered().use { it.writeString(text) }
}
