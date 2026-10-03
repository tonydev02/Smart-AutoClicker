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
package com.buzbuz.smartautoclicker.core.processing.tests

import com.buzbuz.smartautoclicker.core.processing.data.processor.HttpUrlConnectionWebhookHttpClient

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests headers sent by the production webhook HTTP client against a local HTTP server. */
class WebhookHttpClientTests {

    @Test
    fun post_sendsConfiguredJsonContentType() = runBlocking {
        assertContentType("application/json; charset=utf-8")
    }

    @Test
    fun post_sendsConfiguredFormContentType() = runBlocking {
        assertContentType("application/x-www-form-urlencoded; charset=UTF-8")
    }

    private suspend fun assertContentType(expectedContentType: String) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        var receivedContentType: String? = null
        server.createContext("/webhook") { exchange ->
            receivedContentType = exchange.requestHeaders.getFirst("Content-Type")
            exchange.requestBody.use { it.readBytes() }
            exchange.sendResponseHeaders(204, -1)
            exchange.close()
        }
        server.start()

        try {
            HttpUrlConnectionWebhookHttpClient().post(
                url = "http://127.0.0.1:${server.address.port}/webhook",
                contentType = expectedContentType,
                body = "payload".toByteArray(Charsets.UTF_8),
            )
            assertEquals(expectedContentType, receivedContentType)
        } finally {
            server.stop(0)
        }
    }
}
