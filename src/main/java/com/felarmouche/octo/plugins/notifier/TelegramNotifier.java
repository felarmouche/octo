package com.felarmouche.octo.plugins.notifier;

import com.felarmouche.octo.core.notifier.NotifierI;
import com.felarmouche.octo.core.shared.Verdict;
import com.felarmouche.octo.core.shared.listing.Listing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TelegramNotifier implements NotifierI {

    private final RestClient restClient;
    private final String chatId;

    public TelegramNotifier(
            @Value("${telegram.bot.token}") String botToken,
            @Value("${telegram.bot.chat-id}") String chatId) {
        this.chatId = chatId;
        this.restClient = RestClient.create("https://api.telegram.org/bot" + botToken);
    }

    @Override
    public void notify(List<Listing> listings, Set<Verdict.Decision> notifyLevels) {
        if (listings == null || listings.isEmpty()) {
            return;
        }
        List<Listing> notify = listings.stream().
                filter(it -> notifyLevels.contains(it.getVerdict().decision()))
                .toList();
        for (Listing listing : notify) {
            String message = formatListing(listing);
            sendHtmlMessage(message);
        }
    }

    private void sendHtmlMessage(String text) {
        restClient.post()
                .uri("/sendMessage")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "chat_id", chatId,
                        "text", text,
                        "parse_mode", "HTML",
                        "disable_web_page_preview", false // Zeigt Link-Vorschau inkl. Bild an
                ))
                .retrieve()
                .toBodilessEntity();
    }

    private String formatListing(Listing listing) {
        Verdict verdict = listing.getVerdict();
        String badge = getDecisionBadge(verdict != null ? verdict.decision() : null);
        String reason = (verdict != null && verdict.reason() != null)
                ? escape(verdict.reason())
                : "Keine Begründung angegeben";

        String specs = formatSpecs(listing.getProductSpecificFields());

        return """
                %s
                <b>%s</b>
                
                💰 <b>Preis:</b> %d €
                🧠 <b>Einschätzung:</b> <i>%s</i>%s
                
                🔗 <a href="%s"><b>Zum Angebot öffnen</b></a>
                """.formatted(
                badge,
                escape(listing.getTitle()),
                listing.getPriceInEuro(),
                reason,
                specs,
                listing.getLink()
        ).strip();
    }

    private String getDecisionBadge(Verdict.Decision decision) {
        if (decision == null) return "🔎 <b>NEUES ANGEBOT</b>";
        return switch (decision) {
            case BUY -> "🔥 <b>TOP DEAL (KAUFEN)</b>";
            case UNSURE -> "🤔 <b>INTERESSANTES ANGEBOT (PRÜFEN)</b>";
            case NO_BUY -> "❌ <b>KEIN DEAL</b>";
            case NOT_EVALUATABLE -> "↺ <b>ERNEUTE PRÜFUNG...</b>";
        };
    }

    private String formatSpecs(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return "";
        }
        String details = fields.entrySet().stream()
                .map(e -> "  • " + escape(e.getKey()) + ": " + escape(e.getValue()))
                .collect(Collectors.joining("\n"));
        return "\n\n📋 <b>Details:</b>\n" + details;
    }

    private String escape(String input) {
        return input == null ? "" : HtmlUtils.htmlEscape(input);
    }
}