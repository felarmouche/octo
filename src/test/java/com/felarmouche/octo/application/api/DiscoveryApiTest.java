package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** What a frontend asks to render its forms, with the real Listing Types and Plugins. */
@SpringBootTest(properties = {
        "spring.security.user.name=octo",
        "spring.security.user.password=secret",
        "octo.scheduler.first-tick-after=P1D"})
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class DiscoveryApiTest {

    @Autowired
    private MockMvcTester mvc;

    @Test
    void listingTypesComeWithTheirExclusionPresets() {
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].id", v -> v.assertThat().asList().containsExactly("phone", "generic"))
                .hasPathSatisfying("$[0].exclusionPresets[*].id", v -> v.assertThat().asList()
                        .containsExactly("wanted-swap", "damage"))
                .hasPathSatisfying("$[0].exclusionPresets[*].name", v -> v.assertThat().asList()
                        .containsExactly("Gesuche / Tausch", "Schäden"))
                .hasPathSatisfying("$[1].exclusionPresets[*].id", v -> v.assertThat().asList()
                        .containsExactly("wanted-swap"));
    }

    @Test
    void listingTypesAndPluginsHaveGermanTitles() {
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].title", v -> v.assertThat().asList().containsExactly("Handy", "Allgemein"));
        assertThat(get("/api/plugins")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].title", v -> v.assertThat().asList()
                        .containsExactly("Kleinanzeigen", "Rebuy", "Telegram"));
    }

    @Test
    void aListingTypesFilterSchemaHasOptionalFieldsWithConstraintsAndLabelledValues() {
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .doesNotHavePath("$[0].filterSettingsSchema.required")
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.minBattery.title",
                        v -> v.assertThat().isEqualTo("Mindest-Akku (%)"))
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.minBattery.minimum", v -> v.assertThat().isEqualTo(0))
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.minBattery.maximum", v -> v.assertThat().isEqualTo(100))
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.price.properties.maxInCents.minimum",
                        v -> v.assertThat().isEqualTo(0))
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.storages.items.oneOf[0]",
                        v -> v.assertThat().isEqualTo(Map.of("const", "GB_64", "title", "64 GB")))
                .hasPathSatisfying("$[0].filterSettingsSchema.properties.conditions.items.oneOf[*].title",
                        v -> v.assertThat().asList().contains("Sehr gut", "Defekt"))
                .hasPathSatisfying("$[1].filterSettingsSchema.properties.price.title", v -> v.assertThat().isEqualTo("Preis"));
    }

    @Test
    void aListingTypeComesWithTheSchemaOfTheExclusionFilter() {
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[0].exclusionSchema.properties.words.title",
                        v -> v.assertThat().isEqualTo("Ausgeschlossene Wörter"))
                .hasPathSatisfying("$[0].exclusionSchema.properties.words.items.type", v -> v.assertThat().isEqualTo("string"))
                .hasPathSatisfying("$[0].exclusionSchema.properties.presets.title",
                        v -> v.assertThat().isEqualTo("Ausschluss-Vorlagen"));
    }

    @Test
    void theExclusionSchemaOffersExactlyThePresetsOfItsListingType() {
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[0].exclusionSchema.properties.presets.uniqueItems", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$[0].exclusionSchema.properties.presets.items.oneOf", v -> v.assertThat().asList()
                        .containsExactly(Map.of("const", "wanted-swap", "title", "Gesuche / Tausch"),
                                Map.of("const", "damage", "title", "Schäden")))
                .hasPathSatisfying("$[1].exclusionSchema.properties.presets.items.oneOf", v -> v.assertThat().asList()
                        .containsExactly(Map.of("const", "wanted-swap", "title", "Gesuche / Tausch")));
    }

    @Test
    void pluginsForAListingTypeAreOnlyTheCompatibleOnes() {
        assertThat(get("/api/plugins?listingType=generic")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].id", v -> v.assertThat().asList()
                        .containsExactly("kleinanzeigen-finder", "telegram-notifier"));
        assertThat(get("/api/plugins?listingType=phone")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].id", v -> v.assertThat().asList()
                        .containsExactly("kleinanzeigen-finder", "rebuy-evaluator", "telegram-notifier"))
                .hasPathSatisfying("$[*].kind", v -> v.assertThat().asList()
                        .containsExactly("FINDER", "EVALUATOR", "NOTIFIER"));
    }

    @Test
    void withoutAListingTypeEveryPluginIsListed() {
        assertThat(get("/api/plugins")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].id", v -> v.assertThat().asList()
                        .containsExactly("kleinanzeigen-finder", "rebuy-evaluator", "telegram-notifier"))
                .hasPathSatisfying("$[?(@.id == 'rebuy-evaluator')].listingTypes[*]",
                        v -> v.assertThat().asList().containsExactly("phone"));
    }

    @Test
    void amountsInCentsAreIntegersMarkedAsMoney() {
        String price = "$[*].filterSettingsSchema.properties.price.properties";
        assertThat(get("/api/listing-types")).hasStatusOk().bodyJson()
                .hasPathSatisfying(price + ".minInCents.type", v -> v.assertThat().asList()
                        .containsExactly("integer", "integer"))
                .hasPathSatisfying(price + ".minInCents.format", v -> v.assertThat().asList()
                        .containsExactly("money-cents", "money-cents"))
                .hasPathSatisfying(price + ".minInCents.title", v -> v.assertThat().asList()
                        .containsExactly("Mindestpreis", "Mindestpreis"))
                .hasPathSatisfying(price + ".maxInCents.type", v -> v.assertThat().asList()
                        .containsExactly("integer", "integer"))
                .hasPathSatisfying(price + ".maxInCents.format", v -> v.assertThat().asList()
                        .containsExactly("money-cents", "money-cents"))
                .hasPathSatisfying(price + ".maxInCents.title", v -> v.assertThat().asList()
                        .containsExactly("Höchstpreis", "Höchstpreis"));
        String margin = "$[?(@.id == 'rebuy-evaluator')].settingsSchema.properties.minMarginInCents";
        assertThat(get("/api/plugins")).hasStatusOk().bodyJson()
                .hasPathSatisfying(margin + ".type", v -> v.assertThat().asList().containsExactly("integer"))
                .hasPathSatisfying(margin + ".format", v -> v.assertThat().asList().containsExactly("money-cents"))
                .hasPathSatisfying(margin + ".title", v -> v.assertThat().asList().containsExactly("Mindestgewinn"));
    }

    @Test
    void anUnknownListingTypeIsRefusedWithoutAField() {
        assertThat(get("/api/plugins?listingType=car")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .doesNotHavePath("$.errors[0].field")
                .hasPathSatisfying("$.errors[0].message", v -> v.assertThat().asString().contains("car"));
    }

    @Test
    void aPluginsSettingsSchemaHasTypesConstraintsLabelsAndInitialValues() {
        String finder = "$[?(@.id == 'kleinanzeigen-finder')].settingsSchema";
        assertThat(get("/api/plugins?listingType=phone")).hasStatusOk().bodyJson()
                .hasPathSatisfying(finder + ".type", v -> v.assertThat().asList().containsExactly("object"))
                .hasPathSatisfying(finder + ".required[*]", v -> v.assertThat().asList()
                        .containsExactlyInAnyOrder("searchUrl", "maxListings"))
                .hasPathSatisfying(finder + ".additionalProperties", v -> v.assertThat().asList().containsExactly(false))
                .hasPathSatisfying(finder + ".properties.searchUrl.type", v -> v.assertThat().asList().containsExactly("string"))
                .hasPathSatisfying(finder + ".properties.searchUrl.minLength", v -> v.assertThat().asList().containsExactly(1))
                .hasPathSatisfying(finder + ".properties.searchUrl.title", v -> v.assertThat().asList()
                        .containsExactly("Kleinanzeigen-Suchlink"))
                .hasPathSatisfying(finder + ".properties.maxListings.type", v -> v.assertThat().asList().containsExactly("integer"))
                .hasPathSatisfying(finder + ".properties.maxListings.minimum", v -> v.assertThat().asList().containsExactly(1))
                .hasPathSatisfying(finder + ".properties.maxListings.default", v -> v.assertThat().asList().containsExactly(20))
                .hasPathSatisfying("$[?(@.id == 'rebuy-evaluator')].settingsSchema.properties.minAiConfidence.maximum",
                        v -> v.assertThat().asList().containsExactly(1));
    }

    @Test
    void aSchemaListsTheFieldsInTheOrderOfTheSettingsRecord() {
        JsonNode plugins = JsonMapper.shared().readTree(get("/api/plugins").getResponse().getContentAsByteArray());
        JsonNode rebuy = plugins.valueStream().filter(p -> p.get("id").asString().equals("rebuy-evaluator")).findFirst().orElseThrow();

        assertThat(rebuy.get("settingsSchema").get("properties").propertyNames())
                .containsExactly("referenceProductUrl", "minMarginInCents", "minAiConfidence", "maxPhotos");
    }

    @Test
    void theSearchSchemaDescribesTheScheduleWithTimesAndLabelledWeekdays() {
        String schedule = "$.schedule.properties";
        assertThat(get("/api/search-schema")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.schedule.required", v -> v.assertThat().asList().containsExactly("intervalMinutes"))
                .hasPathSatisfying(schedule + ".intervalMinutes.type", v -> v.assertThat().isEqualTo("integer"))
                .hasPathSatisfying(schedule + ".intervalMinutes.minimum", v -> v.assertThat().isEqualTo(1))
                .hasPathSatisfying(schedule + ".intervalMinutes.title", v -> v.assertThat().isEqualTo("Intervall (Minuten)"))
                .hasPathSatisfying(schedule + ".window.title", v -> v.assertThat().isEqualTo("Zeitfenster"))
                .hasPathSatisfying(schedule + ".window.properties.from.type", v -> v.assertThat().isEqualTo("string"))
                .hasPathSatisfying(schedule + ".window.properties.from.format", v -> v.assertThat().isEqualTo("time"))
                .hasPathSatisfying(schedule + ".window.properties.from.title", v -> v.assertThat().isEqualTo("Von"))
                .hasPathSatisfying(schedule + ".window.properties.to.format", v -> v.assertThat().isEqualTo("time"))
                .hasPathSatisfying(schedule + ".window.properties.to.title", v -> v.assertThat().isEqualTo("Bis"))
                .hasPathSatisfying(schedule + ".weekdays.title", v -> v.assertThat().isEqualTo("Wochentage"))
                .hasPathSatisfying(schedule + ".weekdays.description", v -> v.assertThat().isEqualTo("Keine Auswahl: jeden Tag"))
                .hasPathSatisfying(schedule + ".weekdays.uniqueItems", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(schedule + ".weekdays.items.oneOf[*].const", v -> v.assertThat().asList().containsExactly(
                        "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"))
                .hasPathSatisfying(schedule + ".weekdays.items.oneOf[*].title", v -> v.assertThat().asList()
                        .containsExactly("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So"));
    }

    @Test
    void aScheduleWindowIsOptionalButNeedsBothTimes() {
        assertThat(get("/api/search-schema")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.schedule.properties.window.type", v -> v.assertThat().asList()
                        .containsExactlyInAnyOrder("object", "null"))
                .hasPathSatisfying("$.schedule.properties.window.required", v -> v.assertThat().asList()
                        .containsExactlyInAnyOrder("from", "to"));
    }

    @Test
    void theSearchSchemaDescribesTheRetentionRule() {
        String retention = "$.retention.properties";
        assertThat(get("/api/search-schema")).hasStatusOk().bodyJson()
                .doesNotHavePath("$.retention.required")
                .hasPathSatisfying(retention + ".newest.type", v -> v.assertThat().asList()
                        .containsExactlyInAnyOrder("integer", "null"))
                .hasPathSatisfying(retention + ".newest.minimum", v -> v.assertThat().isEqualTo(1))
                .hasPathSatisfying(retention + ".newest.title", v -> v.assertThat().isEqualTo("Neueste behalten"))
                .hasPathSatisfying(retention + ".decisions.title", v -> v.assertThat().isEqualTo("Entscheidungen behalten"))
                .hasPathSatisfying(retention + ".decisions.uniqueItems", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying(retention + ".decisions.description", v -> v.assertThat().isEqualTo("Keine Auswahl: alle Entscheidungen"))
                .hasPathSatisfying(retention + ".decisions.items.oneOf[*].title", v -> v.assertThat().asList()
                        .containsExactly("Kaufen", "Unsicher", "Nicht kaufen", "Nicht bewertbar"));
    }

    @Test
    void theSearchSchemaDescribesTheDecisionsANotifierReports() {
        assertThat(get("/api/search-schema")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.notifierDecisions.type", v -> v.assertThat().isEqualTo("array"))
                .hasPathSatisfying("$.notifierDecisions.title", v -> v.assertThat().isEqualTo("Meldet"))
                .hasPathSatisfying("$.notifierDecisions.uniqueItems", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.notifierDecisions.minItems", v -> v.assertThat().isEqualTo(1))
                .hasPathSatisfying("$.notifierDecisions.items.oneOf", v -> v.assertThat().asList().containsExactly(
                        Map.of("const", "BUY", "title", "Kaufen"),
                        Map.of("const", "UNSURE", "title", "Unsicher"),
                        Map.of("const", "NO_BUY", "title", "Nicht kaufen"),
                        Map.of("const", "NOT_EVALUATABLE", "title", "Nicht bewertbar")));
    }

    @Test
    void aNotifierReportsBuyUnlessChosenOtherwise() {
        assertThat(get("/api/search-schema")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.notifierDecisions.default", v -> v.assertThat().asList().containsExactly("BUY"));
    }

    private MvcTestResult get(String uri) {
        MockHttpSession session = (MockHttpSession) mvc.post().uri("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"octo\",\"password\":\"secret\"}").exchange().getRequest().getSession(false);
        return mvc.get().uri(uri).session(session).exchange();
    }
}
