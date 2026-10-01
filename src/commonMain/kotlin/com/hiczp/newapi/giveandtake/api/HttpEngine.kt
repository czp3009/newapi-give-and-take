package com.hiczp.newapi.giveandtake.api

import io.ktor.client.engine.*

/** Platform default HTTP engine: Curl on native, CIO on JVM. */
expect fun platformHttpEngine(): HttpClientEngine
