package com.hiczp.newapi.giveandtake

internal actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)
