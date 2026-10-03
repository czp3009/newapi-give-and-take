package com.hiczp.newapi.giveandtake

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentVariable(name: String): String? = getenv(name)?.toKString()
