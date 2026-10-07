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


import android.util.Log
import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import com.buzbuz.smartautoclicker.core.common.actions.AndroidActionExecutor
import com.buzbuz.smartautoclicker.core.domain.model.OR
import com.buzbuz.smartautoclicker.core.domain.model.action.Action
import com.buzbuz.smartautoclicker.core.domain.model.action.ChangeCounter
import com.buzbuz.smartautoclicker.core.domain.model.action.Webhook
import com.buzbuz.smartautoclicker.core.domain.model.action.WebhookMode
import com.buzbuz.smartautoclicker.core.domain.model.counter.CounterOperationValue
import com.buzbuz.smartautoclicker.core.domain.model.event.ScreenEvent
import com.buzbuz.smartautoclicker.core.processing.data.processor.ActionExecutor
import com.buzbuz.smartautoclicker.core.processing.data.processor.WebhookHttpClient
import com.buzbuz.smartautoclicker.core.processing.data.processor.WebhookHttpResult
import com.buzbuz.smartautoclicker.core.processing.data.processor.state.ProcessingState
import com.buzbuz.smartautoclicker.core.processing.diagnostics.DiagnosticLogger

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

import kotlinx.coroutines.runBlocking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.mockStatic
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.whenever

/** Tests webhook action execution with a fake HTTP client; no network requests are made. */
class WebhookActionExecutorTests {

    private val androidExecutor: AndroidActionExecutor = mock(AndroidActionExecutor::class.java)
    private val processingState: ProcessingState = mock(ProcessingState::class.java)
    private val counterValues = mutableMapOf<String, Double>()

    @Before
    fun setUp() {
        whenever(processingState.getCounterValue(any())).thenAnswer { invocation ->
            counterValues[invocation.getArgument<String>(0)]
        }
        doAnswer { invocation ->
            counterValues[invocation.getArgument<String>(0)] = invocation.getArgument(1)
            Unit
        }.whenever(processingState).setCounterValue(any(), any())
    }

    @Test
    fun executeTelegram_postsEncodedUtf8FormFieldsAndStringChatIdOnce() = runBlocking {
        val fakeClient = FakeWebhookHttpClient()
        val message = "héllo\n&= {count}"
        counterValues["count"] = 7.0
        execute(
            webhook(mode = WebhookMode.TELEGRAM_BOT, telegramBotToken = "secret-token", telegramChatId = "-100123", telegramMessage = message),
            fakeClient,
        )

        assertEquals(1, fakeClient.requests.size)
        val request = fakeClient.requests.single()
        assertEquals("https://api.telegram.org/botsecret-token/sendMessage", request.url)
        assertEquals("application/x-www-form-urlencoded; charset=UTF-8", request.contentType)
        val form = String(request.body, StandardCharsets.UTF_8).split('&').associate { field ->
            val keyValue = field.split('=', limit = 2)
            URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name()) to
                URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name())
        }
        assertEquals("-100123", form["chat_id"])
        assertEquals("héllo\n&= 7", form["text"])
    }

    @Test
    fun executeTelegram_encodesChannelUsernameChatId() = runBlocking {
        val fakeClient = FakeWebhookHttpClient()
        execute(
            webhook(
                mode = WebhookMode.TELEGRAM_BOT,
                telegramBotToken = "secret-token",
                telegramChatId = "@my_channel",
                telegramMessage = "message",
            ),
            fakeClient,
        )

        val request = fakeClient.requests.single()
        val chatId = String(request.body, StandardCharsets.UTF_8)
            .substringAfter("chat_id=").substringBefore("&")
            .let { URLDecoder.decode(it, StandardCharsets.UTF_8.name()) }
        assertEquals("@my_channel", chatId)
    }

    @Test
    fun executeCustomPost_substitutesCurrentCounterAndPreservesContentType() = runBlocking {
        val fakeClient = FakeWebhookHttpClient()
        counterValues["count"] = 7.5
        execute(
            webhook(
                mode = WebhookMode.CUSTOM_POST,
                customUrl = "https://example.test/hook",
                customContentType = "application/json; charset=utf-8",
                customBody = "{\"count\":\"{count}\"}",
            ),
            fakeClient,
        )

        assertEquals(1, fakeClient.requests.size)
        val request = fakeClient.requests.single()
        assertEquals("https://example.test/hook", request.url)
        assertEquals("application/json; charset=utf-8", request.contentType)
        assertEquals("{\"count\":\"7.5\"}", String(request.body, StandardCharsets.UTF_8))
    }

    @Test
    fun executeWebhook_observesPriorChangeCounterAndFormatsIntegersWithoutDecimal() = runBlocking {
        val fakeClient = FakeWebhookHttpClient()
        counterValues["count"] = 6.0
        val changeCounter = ChangeCounter(
            id = Identifier(databaseId = 2L), eventId = EVENT_ID, name = null, priority = 0,
            counterName = "count", operation = ChangeCounter.OperationType.ADD,
            operationValue = CounterOperationValue.Number(1.0),
        )
        val webhook = webhook(
            mode = WebhookMode.CUSTOM_POST,
            customUrl = "https://example.test",
            customContentType = "text/plain",
            customBody = "{count}",
        )
        execute(listOf(changeCounter, webhook), fakeClient)
        assertEquals("7", String(fakeClient.requests.single().body, StandardCharsets.UTF_8))
    }

    @Test
    fun executeWebhook_non2xxAndThrownTimeoutDoNotEscapeOrRetry() = runBlocking {
        val webhook = webhook(
            mode = WebhookMode.CUSTOM_POST,
            customUrl = "https://example.test",
            customContentType = "text/plain",
            customBody = "body",
        )
        val failingStatus = FakeWebhookHttpClient(result = WebhookHttpResult(503))
        execute(webhook, failingStatus)
        assertEquals(1, failingStatus.requests.size)

        val timeout = FakeWebhookHttpClient(failure = java.net.SocketTimeoutException("secret exception text"))
        execute(webhook, timeout)
        assertEquals(1, timeout.requests.size)
    }
    @Test
    fun webhookDiagnosticsContainOnlySafeMetadata() = runBlocking {
        val secretToken = "private-bot-token"
        val privateChatId = "-100private-chat"
        val privateMessage = "do not log this payload"
        val records = mutableListOf<String>()
        val logger = object : DiagnosticLogger {
            override fun startSession(scenarioId: Long, details: String) = Unit
            override fun log(category: String, message: String) {
                records += message
            }
            override fun endSession(state: String, reason: String) = Unit
        }
        val action = webhook(
            mode = WebhookMode.TELEGRAM_BOT,
            telegramBotToken = secretToken,
            telegramChatId = privateChatId,
            telegramMessage = privateMessage,
        )
        val http = FakeWebhookHttpClient()
        mockStatic(Log::class.java).use {
            ActionExecutor(
                androidExecutor,
                processingState,
                randomize = false,
                webhookHttpClient = http,
                diagnosticLogger = logger,
            ).executeActions(
                event = ScreenEvent(
                    EVENT_ID, Identifier(databaseId = 3L), "Event", OR, listOf(action), emptyList(),
                    true, 0, cooldownMs = 0, keepDetecting = false,
                ),
            )
        }

        val diagnostics = records.joinToString("\\n")
        assertTrue(diagnostics.contains("WEBHOOK START"))
        assertTrue(diagnostics.contains("WEBHOOK END"))
        listOf(secretToken, privateChatId, privateMessage, "chat_id=", "text=").forEach {
            assertTrue("Diagnostic log leaked $it", !diagnostics.contains(it))
        }
    }

    @Test
    fun executeTelegram_overLimitDoesNotSend() = runBlocking {
        val fakeClient = FakeWebhookHttpClient()
        execute(
            webhook(
                mode = WebhookMode.TELEGRAM_BOT,
                telegramBotToken = "secret-token",
                telegramChatId = "1",
                telegramMessage = "x".repeat(4097),
            ),
            fakeClient,
        )
        assertTrue(fakeClient.requests.isEmpty())
    }

    private suspend fun execute(webhook: Webhook, client: FakeWebhookHttpClient) = execute(listOf(webhook), client)

    private suspend fun execute(actions: List<Action>, client: FakeWebhookHttpClient) {
        mockStatic(Log::class.java).use {
            ActionExecutor(androidExecutor, processingState, randomize = false, webhookHttpClient = client)
                .executeActions(
                    event = ScreenEvent(EVENT_ID, Identifier(databaseId = 3L), "Event", OR, actions, emptyList(), true, 0, cooldownMs = 0, keepDetecting = false),
                )
        }
    }

    private fun webhook(
        mode: WebhookMode,
        telegramBotToken: String? = null,
        telegramChatId: String? = null,
        telegramMessage: String? = null,
        customUrl: String? = null,
        customContentType: String? = null,
        customBody: String? = null,
    ) = Webhook(
        id = Identifier(databaseId = 1L), eventId = EVENT_ID, name = null, priority = 1, mode = mode,
        telegramBotToken = telegramBotToken, telegramChatId = telegramChatId,
        telegramMessage = telegramMessage, customUrl = customUrl,
        customContentType = customContentType, customBody = customBody,
    )

    private class FakeWebhookHttpClient(
        private val result: WebhookHttpResult = WebhookHttpResult(200),
        private val failure: Exception? = null,
    ) : WebhookHttpClient {
        val requests = mutableListOf<Request>()

        override suspend fun post(url: String, contentType: String, body: ByteArray): WebhookHttpResult {
            requests += Request(url, contentType, body.copyOf())
            failure?.let { throw it }
            return result
        }
    }

    private data class Request(val url: String, val contentType: String, val body: ByteArray)

    private companion object {
        val EVENT_ID = Identifier(databaseId = 42L)
    }
}
