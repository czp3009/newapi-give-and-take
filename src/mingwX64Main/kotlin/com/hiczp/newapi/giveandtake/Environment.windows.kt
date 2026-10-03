package com.hiczp.newapi.giveandtake

import kotlinx.cinterop.*
import platform.windows.GetEnvironmentVariableW

@OptIn(ExperimentalForeignApi::class)
internal actual fun getEnvironmentVariable(name: String): String? = memScoped {
    // Read the UTF-16 environment directly; the CRT's narrow-character copy can lose Unicode values.
    val size = GetEnvironmentVariableW(name.wcstr.ptr, null, 0u)
    if (size == 0u) return null
    val buffer = allocArray<UShortVar>(size.toInt())
    val length = GetEnvironmentVariableW(name.wcstr.ptr, buffer, size)
    if (length == 0u) return null
    check(length < size) { "Failed to read environment variable $name" }
    buffer.toKStringFromUtf16()
}
