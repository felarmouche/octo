package com.felarmouche.octo.plugins.telegram;

import java.util.Objects;

/** @param chatId the Telegram chat the Alerts go to. The bot token is an operator setting. */
public record TelegramSettings(String chatId) {
    public TelegramSettings {
        Objects.requireNonNull(chatId, "chatId");
        if (chatId.isBlank()) throw new IllegalArgumentException("chatId must not be blank");
    }
}
