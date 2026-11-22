package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.types.generic.GenericListingType;
import com.felarmouche.octo.types.phone.PhoneListingType;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Searches managed through the HTTP API, against PostgreSQL, with fake Plugins next to the real ones. */
@SpringBootTest(properties = {
        "spring.security.user.name=" + SearchApiTest.USER,
        "spring.security.user.password=" + SearchApiTest.PASSWORD,
        "octo.searches.first-run-after=P1D"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SearchApiTest.FakePlugins.class})
class SearchApiTest {

    static final String USER = "octo";
    static final String PASSWORD = "secret";

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private com.felarmouche.octo.application.search.SearchRunner runner;
    @Autowired
    private FakeFinder finder;
    @Autowired
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp() {
        jdbc.sql("delete from search").update();
        finder.offers.clear();
        notifier.sent.clear();
    }

    @Test
    void aCreatedSearchCanBeReadAndIsListedAsActive() {
        MvcTestResult created = post("/api/searches", ps5Search());

        assertThat(created).hasStatus(HttpStatus.CREATED);
        String id = id(created);
        assertThat(get("/api/searches/" + id)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.name", v -> v.assertThat().isEqualTo("PS5 unter 250 €"))
                .hasPathSatisfying("$.listingType", v -> v.assertThat().isEqualTo("generic"))
                .hasPathSatisfying("$.active", v -> v.assertThat().isEqualTo(true))
                .hasPathSatisfying("$.finders[0].settings.query", v -> v.assertThat().isEqualTo("ps5"))
                .hasPathSatisfying("$.notifiers[0].decisions", v -> v.assertThat().asList().containsExactly("BUY"))
                .hasPathSatisfying("$.typeFilter.price.maxInCents", v -> v.assertThat().isEqualTo(250_00));
        assertThat(get("/api/searches")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].id", v -> v.assertThat().asList().containsExactly(id));
    }

    @Test
    void theApiNeedsTheConfiguredUser() {
        assertThat(mvc.get().uri("/api/searches")).hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.get().uri("/api/searches").header(HttpHeaders.AUTHORIZATION, basic(USER, "wrong")))
                .hasStatus(HttpStatus.UNAUTHORIZED);
        assertThat(mvc.post().uri("/api/searches").contentType(MediaType.APPLICATION_JSON).content(ps5Search()))
                .hasStatus(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void aPausedSearchDoesNotRunUntilItIsResumed() {
        String id = id(post("/api/searches", ps5Search()));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));

        assertThat(post("/api/searches/" + id + "/pause", "")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.active", v -> v.assertThat().isEqualTo(false));
        runner.runAll();
        assertThat(notifier.sent).isEmpty();
        assertThat(get("/api/searches/" + id)).bodyJson().hasPathSatisfying("$.lastRun", v -> v.assertThat().isNull());

        assertThat(post("/api/searches/" + id + "/resume", "")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.active", v -> v.assertThat().isEqualTo(true));
        runner.runAll();
        assertThat(notifier.sentTitles()).containsExactly("PS5 Disc");
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.lastRun.ok", v -> v.assertThat().isEqualTo(true));
    }

    @Test
    void anEditAppliesFromTheNextRunAndKeepsWhatTheSearchHasSeen() {
        String id = id(post("/api/searches", ps5Search("BUY")));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        runner.runAll();
        notifier.sent.clear();

        // now also Listings above 250 € are rejected-and-reported as NO_BUY
        String edited = ps5Search("BUY,NO_BUY").replace("\"name\": \"PS5 unter 250 €\"", "\"name\": \"PS5\"");
        assertThat(put("/api/searches/" + id, edited)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.name", v -> v.assertThat().isEqualTo("PS5"));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00), listing("PS5 Pro", 600_00)));
        runner.runAll();

        assertThat(notifier.sentTitles()).containsExactly("PS5 Pro");
    }

    @Test
    void eachNotifierSlotReportsOnlyItsDecisions() {
        String body = ps5Search().replace(
                "\"notifiers\": [{\"plugin\": \"recording-notifier\", \"decisions\": [\"BUY\"], \"settings\": {\"channel\": \"me\"}}]",
                "\"notifiers\": [{\"plugin\": \"recording-notifier\", \"decisions\": [\"NO_BUY\"], \"settings\": {\"channel\": \"me\"}}]");
        post("/api/searches", body);
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00), listing("PS5 defekt", 100_00)));

        runner.runAll();

        assertThat(notifier.sentTitles()).containsExactly("PS5 defekt");
    }

    @Test
    void deletingASearchRemovesItsListingHistoryAndSeenFingerprints() {
        String id = id(post("/api/searches", ps5Search()));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        runner.runAll();
        assertThat(rows("listing_history", id)).isEqualTo(1);
        assertThat(rows("seen_fingerprint", id)).isEqualTo(1);

        assertThat(delete("/api/searches/" + id)).hasStatus(HttpStatus.NO_CONTENT);

        assertThat(get("/api/searches/" + id)).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(rows("listing_history", id)).isZero();
        assertThat(rows("seen_fingerprint", id)).isZero();
    }

    @Test
    void theListingTypeOfAnExistingSearchCannotChange() {
        String id = id(post("/api/searches", ps5Search()));

        MvcTestResult result = put("/api/searches/" + id,
                ps5Search().replace("\"listingType\": \"generic\"", "\"listingType\": \"phone\""));

        assertRefused(result, "listingType");
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.listingType", v -> v.assertThat().isEqualTo("generic"));
    }

    @Test
    void savingRefusesUnknownPlugins() {
        assertRefused(post("/api/searches", ps5Search().replace("\"fake-finder\"", "\"no-such-finder\"")),
                "finders[0].plugin");
    }

    @Test
    void savingRefusesAPluginOfTheWrongKind() {
        assertRefused(post("/api/searches", ps5Search().replace("\"fake-finder\"", "\"recording-notifier\"")),
                "finders[0].plugin");
    }

    @Test
    void savingRefusesAPluginForAnotherListingType() {
        String withRebuy = ps5Search().replace("\"notifiers\":", """
                "evaluator": {"plugin": "rebuy-evaluator", "settings": {"referenceProductUrl": "https://www.rebuy.de/x",
                              "minMarginInCents": 2000, "minAiConfidence": 0.7, "maxPhotos": 4}},
                "notifiers":""");

        assertRefused(post("/api/searches", withRebuy), "evaluator.plugin");
    }

    @Test
    void savingRefusesInvalidSettingsAndNamesTheField() {
        String body = ps5Search()
                .replace("\"finders\": [{\"plugin\": \"fake-finder\", \"settings\": {\"query\": \"ps5\"}}]", """
                        "finders": [{"plugin": "fake-finder", "settings": {"query": "ps5"}},
                                    {"plugin": "kleinanzeigen-finder", "settings": {"searchUrl": "https://x", "maxListings": 0}}]""");

        assertRefused(post("/api/searches", body), "finders[1].settings.maxListings");
        assertRefused(post("/api/searches", ps5Search().replace("\"maxInCents\": 25000", "\"maxInCents\": -1")),
                "typeFilter.price.maxInCents");
        assertRefused(post("/api/searches", ps5Search().replace("{\"query\": \"ps5\"}", "{\"qeury\": \"ps5\"}")),
                "finders[0].settings.qeury");
        assertRefused(post("/api/searches", ps5Search().replace("\"words\": [\"defekt\"]", "\"presets\": [\"damage\"]")),
                "exclusion.presets");
    }

    @Test
    void savingRefusesUnknownFieldsAndLeftOutNumbers() {
        assertRefused(post("/api/searches", ps5Search().replace("\"retention\":", "\"retension\":")), "retension");
        String phoneWithRebuy = ps5Search()
                .replace("\"listingType\": \"generic\"", "\"listingType\": \"phone\"")
                .replace("\"notifiers\":", """
                        "evaluator": {"plugin": "rebuy-evaluator", "settings": {"referenceProductUrl": "https://www.rebuy.de/x",
                                      "minMarginInCents": 2000, "maxPhotos": 4}},
                        "notifiers":""");
        assertRefused(post("/api/searches", phoneWithRebuy), "evaluator.settings.minAiConfidence");
    }

    @Test
    void aReadSearchCanBeSentBackAsAnEdit() {
        String id = id(post("/api/searches", ps5Search()));
        String read = new String(get("/api/searches/" + id).getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);

        assertThat(put("/api/searches/" + id, read)).hasStatusOk();
    }

    @Test
    void savingRefusesASearchWithoutFinderOrNotifier() {
        assertRefused(post("/api/searches", ps5Search()
                .replace("[{\"plugin\": \"fake-finder\", \"settings\": {\"query\": \"ps5\"}}]", "[]")), "finders");
        assertRefused(post("/api/searches", ps5Search().replaceAll("\"notifiers\": \\[.*]", "\"notifiers\": []")),
                "notifiers");
        assertRefused(post("/api/searches", ps5Search("")), "notifiers[0].decisions");
        assertThat(get("/api/searches")).bodyJson().hasPathSatisfying("$", v -> v.assertThat().asList().isEmpty());
    }

    @Test
    void anUnknownSearchIsNotFound() {
        assertThat(get("/api/searches/nope")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(put("/api/searches/nope", ps5Search())).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(post("/api/searches/nope/pause", "")).hasStatus(HttpStatus.NOT_FOUND);
        assertThat(delete("/api/searches/nope")).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- helpers -------------------------------------------------------------------------------

    private static void assertRefused(MvcTestResult result, String field) {
        assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .hasPathSatisfying("$.errors[*].field", v -> v.assertThat().asList().containsExactly(field))
                .hasPathSatisfying("$.errors[0].message", v -> v.assertThat().asString().isNotBlank());
    }

    private int rows(String table, String searchId) {
        return jdbc.sql("select count(*) from " + table + " where search_id = ?").param(searchId).query(Integer.class).single();
    }

    private static String ps5Search() {
        return ps5Search("BUY");
    }

    private static String ps5Search(String decisions) {
        return """
                {
                  "name": "PS5 unter 250 €",
                  "listingType": "generic",
                  "typeFilter": {"price": {"maxInCents": 25000}},
                  "exclusion": {"words": ["defekt"]},
                  "finders": [{"plugin": "fake-finder", "settings": {"query": "ps5"}}],
                  "notifiers": [{"plugin": "recording-notifier", "decisions": [%s], "settings": {"channel": "me"}}],
                  "retention": {"newest": 50}
                }""".formatted(quoted(decisions));
    }

    private static String quoted(String decisions) {
        return decisions.isBlank() ? "" : "\"" + String.join("\",\"", decisions.split(",")) + "\"";
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD)).exchange();
    }

    private MvcTestResult post(String uri, String body) {
        return mvc.post().uri(uri).header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult put(String uri, String body) {
        return mvc.put().uri(uri).header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD))
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult delete(String uri) {
        return mvc.delete().uri(uri).header(HttpHeaders.AUTHORIZATION, basic(USER, PASSWORD)).exchange();
    }

    private static String id(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return tools.jackson.databind.json.JsonMapper.shared().readTree(body).get("id").asString();
    }

    private static String basic(String user, String password) {
        return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
    }

    private static Listing listing(String title, int price) {
        return new Listing("fake", title, title, "", "https://example.org/" + title.hashCode(), List.of(),
                Instant.parse("2026-11-22T10:00:00Z"), java.util.Optional.of(price), Map.of(), java.util.Optional.empty());
    }

    // --- fake Plugins ----------------------------------------------------------------------------

    @TestConfiguration(proxyBeanMethods = false)
    static class FakePlugins {
        @Bean
        FakeFinder fakeFinder() {
            return new FakeFinder();
        }

        @Bean
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    record FakeFinderSettings(@NotBlank String query) {
    }

    /** Offers, per query, the Listings a test put there. */
    static class FakeFinder implements Finder<FakeFinderSettings> {
        final Map<String, List<Listing>> offers = new ConcurrentHashMap<>();

        @Override
        public Result find(FakeFinderSettings settings, ListingType<?> listingType) {
            return Result.of(offers.getOrDefault(settings.query(), List.of()));
        }

        @Override
        public String id() {
            return "fake-finder";
        }

        @Override
        public Set<String> listingTypes() {
            return Set.of(GenericListingType.ID, PhoneListingType.ID);
        }

        @Override
        public Class<FakeFinderSettings> settingsType() {
            return FakeFinderSettings.class;
        }
    }

    record RecordingSettings(@NotBlank String channel) {
    }

    static class RecordingNotifier implements Notifier<RecordingSettings> {
        final List<Alert> sent = new CopyOnWriteArrayList<>();

        @Override
        public void send(List<Alert> alerts, RecordingSettings settings) {
            sent.addAll(alerts);
        }

        List<String> sentTitles() {
            return new ArrayList<>(sent.stream().map(a -> a.listing().title()).toList());
        }

        @Override
        public String id() {
            return "recording-notifier";
        }

        @Override
        public Class<RecordingSettings> settingsType() {
            return RecordingSettings.class;
        }
    }
}
