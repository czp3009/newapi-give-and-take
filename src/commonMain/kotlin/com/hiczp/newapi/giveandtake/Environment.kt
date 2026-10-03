package com.hiczp.newapi.giveandtake

/** Reads a process environment value; configuration resolution treats null and empty strings as absent. */
internal expect fun getEnvironmentVariable(name: String): String?
