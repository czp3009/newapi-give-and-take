package com.hiczp.newapi.giveandtake.api

/**
 * Thrown when the new-api server reports a business failure: an HTTP 200
 * response whose envelope carries `success=false` (and an error `message`).
 *
 * Detected centrally by a response interceptor installed on the HTTP client
 * (see [NewApiClient.create]), so every request through [NewApi] fails with
 * this exception instead of silently returning an envelope without data.
 * HTTP level failures are reported as Ktor's
 * [io.ktor.client.plugins.ResponseException] via `ExpectSuccess` instead.
 */
class NewApiException(message: String) : Exception(message)
