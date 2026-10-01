package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.*
import io.ktor.client.engine.cio.*

actual fun platformHttpEngine(): HttpClientEngine = CIO.create()
