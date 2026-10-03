/*
 * Copyright (C) 2026 Kevin Buzeau
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
package com.buzbuz.smartautoclicker.core.domain.model.action

import com.buzbuz.smartautoclicker.core.base.identifier.Identifier
import java.net.URI

/** Maximum Telegram Bot API message length accepted by a Webhook action. */
const val MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH = 4096

/** Delivery mode for a [Webhook] action. */
enum class WebhookMode {
    TELEGRAM_BOT,
    CUSTOM_POST,
}

/** Sends a Telegram Bot message or an HTTP POST request. */
data class Webhook(
    override val id: Identifier,
    override val eventId: Identifier,
    override val name: String? = null,
    override var priority: Int,
    val mode: WebhookMode,
    val telegramBotToken: String? = null,
    val telegramChatId: String? = null,
    val telegramMessage: String? = null,
    val customUrl: String? = null,
    val customContentType: String? = null,
    val customBody: String? = null,
) : Action() {

    override fun hashCodeNoIds(): Int {
        var result = name?.hashCode() ?: 0
        result = 31 * result + mode.hashCode()
        result = 31 * result + (telegramBotToken?.hashCode() ?: 0)
        result = 31 * result + (telegramChatId?.hashCode() ?: 0)
        result = 31 * result + (telegramMessage?.hashCode() ?: 0)
        result = 31 * result + (customUrl?.hashCode() ?: 0)
        result = 31 * result + (customContentType?.hashCode() ?: 0)
        result = 31 * result + (customBody?.hashCode() ?: 0)
        return result
    }

    override fun deepCopy(): Webhook = copy()

    override fun isComplete(): Boolean = super.isComplete() && when (mode) {
        WebhookMode.TELEGRAM_BOT ->
            !telegramBotToken.isNullOrBlank() &&
                !telegramChatId.isNullOrBlank() &&
                !telegramMessage.isNullOrBlank() &&
                telegramMessage.length <= MAX_WEBHOOK_TELEGRAM_MESSAGE_LENGTH

        WebhookMode.CUSTOM_POST ->
            isValidHttpUrl(customUrl) &&
                !customContentType.isNullOrBlank() &&
                customBody != null
    }


    /** Keep credentials and request content out of diagnostic strings. */
    override fun toString(): String =
        "Webhook(id=$id, eventId=$eventId, mode=$mode, configuration=<redacted>)"

    private fun isValidHttpUrl(value: String?): Boolean {
        if (value.isNullOrBlank()) return false
        return try {
            val uri = URI(value)
            uri.isAbsolute &&
                uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank()
        } catch (_: Exception) {
            false
        }
    }
}
