package com.hiczp.newapi.giveandtake.api

/**
 * A new-api business failure or a response missing data required by [NewApiClient].
 *
 * Clients created by [createHttpClient] throw this for 2xx envelopes with `success=false`.
 * Nonblank server messages are preserved; blank business-error messages are replaced with `new-api request failed`.
 */
class NewApiException(message: String) : Exception(message)
