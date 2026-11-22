package com.felarmouche.octo.plugins.telegram;

import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.core.plugin.PluginException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Sends one HTML message per Alert to the chat in its settings, through the one global bot. */
@Component(TelegramNotifier.ID)
public class TelegramNotifier implements Notifier<TelegramSettings> {

    public static final String ID = "telegram-notifier";

    private final RestClient restClient;

    public TelegramNotifier(@Value("${telegram.bot.token}") String botToken) {
        this.restClient = RestClient.create("https://api.telegram.org/bot" + botToken);
    }

    @Override
    public void send(List<Alert> alerts, TelegramSettings settings) {
        int failed = 0;
        RuntimeException lastFailure = null;
        for (Alert alert : alerts) {
            try {
                restClient.post()
                        .uri("/sendMessage")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(Map.of(
                                "chat_id", settings.chatId(),
                                "text", format(alert),
                                "parse_mode", "HTML",
                                "disable_web_page_preview", false)) // link preview shows the ad's image
                        .retrieve()
                        .toBodilessEntity();
            } catch (RuntimeException e) {
                failed++;
                lastFailure = e;
            }
        }
        if (lastFailure != null) {
            throw new PluginException(ID, "%d von %d Nachrichten an Telegram fehlgeschlagen: %s"
                    .formatted(failed, alerts.size(), lastFailure.getMessage()), lastFailure);
        }
    }

    static String format(Alert alert) {
        Listing listing = alert.listing();
        return """
                %s
                <b>%s</b>

                💰 <b>Preis:</b> %s
                🧠 <b>Einschätzung:</b> <i>%s</i>%s

                🔗 <a href="%s"><b>Zum Angebot öffnen</b></a>
                """.formatted(
                badge(alert.verdict().decision()),
                escape(listing.title()),
                price(listing),
                escape(alert.verdict().reason()),
                details(alert.values()),
                escape(listing.link())
        ).strip();
    }

    private static String badge(Decision decision) {
        return switch (decision) {
            case BUY -> "🔥 <b>TOP DEAL (KAUFEN)</b>";
            case UNSURE -> "🤔 <b>INTERESSANTES ANGEBOT (PRÜFEN)</b>";
            case NO_BUY -> "❌ <b>KEIN DEAL</b>";
            case NOT_EVALUATABLE -> "↺ <b>ERNEUTE PRÜFUNG...</b>";
        };
    }

    private static String price(Listing listing) {
        return listing.priceInCents()
                .map(cents -> cents % 100 == 0
                        ? "%d €".formatted(cents / 100)
                        : "%d,%02d €".formatted(cents / 100, cents % 100))
                .orElse("kein Preis angegeben");
    }

    private static String details(List<LabelledValue> values) {
        if (values.isEmpty()) return "";
        return "\n\n📋 <b>Details:</b>\n" + values.stream()
                .map(v -> "  • " + escape(v.label()) + ": " + escape(v.value()))
                .collect(Collectors.joining("\n"));
    }

    private static String escape(String input) {
        return input == null ? "" : HtmlUtils.htmlEscape(input);
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Telegram";
    }

    @Override
    public Class<TelegramSettings> settingsType() {
        return TelegramSettings.class;
    }
}
