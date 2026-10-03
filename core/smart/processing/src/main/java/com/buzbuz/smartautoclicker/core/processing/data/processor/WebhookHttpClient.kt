/*
 * Copyright (C) 2024 Kevin Buzeau
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.buzbuz.smartautoclicker.core.processing.data.processor

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** HTTP response details needed by webhook execution. */
internal data class WebhookHttpResult(val statusCode: Int)

/** Injectable single-request HTTP client for webhook actions. */
internal fun interface WebhookHttpClient {
    suspend fun post(url: String, contentType: String, body: ByteArray): WebhookHttpResult
}

/** Production [WebhookHttpClient] backed by [HttpURLConnection]. */
internal class HttpUrlConnectionWebhookHttpClient : WebhookHttpClient {

    override suspend fun post(url: String, contentType: String, body: ByteArray): WebhookHttpResult =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.doOutput = true
                connection.instanceFollowRedirects = false
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { output -> output.write(body) }

                val statusCode = connection.responseCode
                val responseStream = if (statusCode >= HttpURLConnection.HTTP_BAD_REQUEST) {
                    connection.errorStream
                } else {
                    connection.inputStream
                }
                responseStream?.use { input ->
                    val buffer = ByteArray(RESPONSE_DRAIN_BUFFER_SIZE)
                    while (input.read(buffer) != -1) Unit
                }
                WebhookHttpResult(statusCode)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 5_000
        const val READ_TIMEOUT_MS = 10_000
        const val RESPONSE_DRAIN_BUFFER_SIZE = 1_024
    }
}
