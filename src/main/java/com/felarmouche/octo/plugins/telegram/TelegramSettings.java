package com.felarmouche.octo.plugins.telegram;

import jakarta.validation.constraints.NotBlank;

/** @param chatId the Telegram chat the Alerts go to. The bot token is an operator setting. */
public record TelegramSettings(@NotBlank String chatId) {
}
