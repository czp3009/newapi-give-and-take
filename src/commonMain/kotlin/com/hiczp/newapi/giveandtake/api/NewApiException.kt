package com.hiczp.newapi.giveandtake.api

/**
 * A business failure reported in a 2xx response envelope with `success=false`.
 *
 * Clients created by [NewApiClient.create] throw this from a response interceptor.
 * Nonblank server messages are preserved; blank messages are replaced with
 * `new-api request failed`.
 */
class NewApiException(message: String) : Exception(message)
