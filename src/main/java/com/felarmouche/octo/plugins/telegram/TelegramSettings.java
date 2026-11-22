package com.felarmouche.octo.plugins.telegram;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** @param chatId the Telegram chat the Alerts go to. The bot token is an operator setting. */
public record TelegramSettings(
        @Schema(title = "Telegram-Chat-ID") @NotBlank String chatId) {
}
