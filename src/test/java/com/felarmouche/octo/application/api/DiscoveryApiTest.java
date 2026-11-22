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
    void anUnknownListingTypeIsRefused() {
        assertThat(get("/api/plugins?listingType=car")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .hasPathSatisfying("$.errors[*].field", v -> v.assertThat().asList().containsExactly("listingType"));
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

    private MvcTestResult get(String uri) {
        MockHttpSession session = (MockHttpSession) mvc.post().uri("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"octo\",\"password\":\"secret\"}").exchange().getRequest().getSession(false);
        return mvc.get().uri(uri).session(session).exchange();
    }
}
