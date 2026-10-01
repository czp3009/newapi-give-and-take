package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.*
import io.ktor.client.engine.curl.*

actual fun platformHttpEngine(): HttpClientEngine = Curl.create()
