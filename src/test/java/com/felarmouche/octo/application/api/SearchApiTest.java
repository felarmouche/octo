package com.felarmouche.octo.application.api;

import com.felarmouche.octo.TestcontainersConfiguration;
import com.felarmouche.octo.application.search.SearchRunner;
import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Evaluator;
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
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

/** Searches managed through the HTTP API, against PostgreSQL, with fake Plugins next to the real ones. */
@SpringBootTest(properties = {
        "spring.security.user.name=" + SearchApiTest.USER,
        "spring.security.user.password=" + SearchApiTest.PASSWORD,
        "octo.scheduler.first-tick-after=P1D"})
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, SearchApiTest.FakePlugins.class, SearchApiTest.TestClock.class})
class SearchApiTest {

    static final String USER = "octo";
    static final String PASSWORD = "secret";
    /** Monday 2026-11-23, 12:00 in Berlin. */
    static final Instant MONDAY_NOON = Instant.parse("2026-11-23T11:00:00Z");

    @Autowired
    private MockMvcTester mvc;
    @Autowired
    private JdbcClient jdbc;
    @Autowired
    private SearchRunner runner;
    @Autowired
    private MutableClock clock;
    @Autowired
    private FakeFinder finder;
    @Autowired
    private RecordingNotifier notifier;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        jdbc.sql("delete from search").update();
        finder.reset();
        notifier.sent.clear();
        clock.set(MONDAY_NOON);
        session = login();
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
    void aPausedSearchDoesNotRunUntilItIsResumed() {
        String id = id(post("/api/searches", ps5Search()));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));

        assertThat(post("/api/searches/" + id + "/pause", "")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.active", v -> v.assertThat().isEqualTo(false));
        runDue();
        assertThat(notifier.sent).isEmpty();
        assertThat(get("/api/searches/" + id)).bodyJson().hasPathSatisfying("$.lastRun", v -> v.assertThat().isNull());

        assertThat(post("/api/searches/" + id + "/resume", "")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.active", v -> v.assertThat().isEqualTo(true));
        runDue();
        assertThat(notifier.sentTitles()).containsExactly("PS5 Disc");
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.lastRun.ok", v -> v.assertThat().isEqualTo(true));
    }

    @Test
    void anEditAppliesFromTheNextRunAndKeepsWhatTheSearchHasSeen() {
        String id = id(post("/api/searches", ps5Search("BUY")));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        runDue();
        notifier.sent.clear();

        // now also Listings above 250 € are rejected-and-reported as NO_BUY
        String edited = ps5Search("BUY,NO_BUY").replace("\"name\": \"PS5 unter 250 €\"", "\"name\": \"PS5\"");
        assertThat(put("/api/searches/" + id, edited)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.name", v -> v.assertThat().isEqualTo("PS5"));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00), listing("PS5 Pro", 600_00)));
        clock.advance(Duration.ofMinutes(5));
        runDue();

        assertThat(notifier.sentTitles()).containsExactly("PS5 Pro");
    }

    @Test
    void eachNotifierSlotReportsOnlyItsDecisions() {
        String body = ps5Search().replace(
                "\"notifiers\": [{\"plugin\": \"recording-notifier\", \"decisions\": [\"BUY\"], \"settings\": {\"channel\": \"me\"}}]",
                "\"notifiers\": [{\"plugin\": \"recording-notifier\", \"decisions\": [\"NO_BUY\"], \"settings\": {\"channel\": \"me\"}}]");
        post("/api/searches", body);
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00), listing("PS5 defekt", 100_00)));

        runDue();

        assertThat(notifier.sentTitles()).containsExactly("PS5 defekt");
    }

    @Test
    void deletingASearchRemovesItsListingHistoryAndSeenFingerprints() {
        String id = id(post("/api/searches", ps5Search()));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        runDue();
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

    // --- Schedule ------------------------------------------------------------------------------

    @Test
    void theScheduleIsPartOfTheSearch() {
        String body = ps5Search().replace("\"schedule\": {\"intervalMinutes\": 5}", """
                "schedule": {"intervalMinutes": 10, "window": {"from": "11:00", "to": "17:00"},
                             "weekdays": ["MONDAY", "FRIDAY"]}""");
        String id = id(post("/api/searches", body));

        assertThat(get("/api/searches/" + id)).hasStatusOk().bodyJson()
                .hasPathSatisfying("$.schedule.intervalMinutes", v -> v.assertThat().isEqualTo(10))
                .hasPathSatisfying("$.schedule.window.from", v -> v.assertThat().isEqualTo("11:00:00"))
                .hasPathSatisfying("$.schedule.window.to", v -> v.assertThat().isEqualTo("17:00:00"))
                .hasPathSatisfying("$.schedule.weekdays", v -> v.assertThat().asList()
                        .containsExactlyInAnyOrder("MONDAY", "FRIDAY"));
    }

    @Test
    void savingRefusesAnInvalidSchedule() {
        assertRefused(post("/api/searches", ps5Search().replace("\"intervalMinutes\": 5", "\"intervalMinutes\": 0")),
                "schedule.intervalMinutes");
        assertRefused(post("/api/searches", ps5Search().replace("\"schedule\": {\"intervalMinutes\": 5},", "")),
                "schedule.intervalMinutes");
        assertRefused(post("/api/searches", ps5Search().replace("\"intervalMinutes\": 5",
                "\"intervalMinutes\": 5, \"window\": {\"from\": \"17:00\", \"to\": \"11:00\"}")), "schedule.window");
        assertRefused(post("/api/searches", ps5Search().replace("\"intervalMinutes\": 5",
                "\"intervalMinutes\": 5, \"weekdays\": [\"MONTAG\"]")), "schedule.weekdays[0]");
    }

    @Test
    void aNewSearchRunsAtOnceAndThenAfterTheIntervalFromTheEndOfTheRun() {
        String id = id(post("/api/searches", ps5Search()));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));

        runDue();
        assertThat(finder.calls).hasSize(1);
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.nextRun", v -> v.assertThat().isEqualTo("2026-11-23T11:05:00Z"));

        clock.advance(Duration.ofMinutes(4));
        runDue();
        assertThat(finder.calls).hasSize(1);

        clock.advance(Duration.ofMinutes(1));
        runDue();
        assertThat(finder.calls).hasSize(2);
    }

    @Test
    void aSearchOutsideItsWindowWaitsForTheWindowToOpen() {
        String body = ps5Search().replace("\"intervalMinutes\": 5",
                "\"intervalMinutes\": 5, \"window\": {\"from\": \"14:00\", \"to\": \"17:00\"}");
        String id = id(post("/api/searches", body));

        runDue();
        assertThat(finder.calls).isEmpty();
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.nextRun", v -> v.assertThat().isEqualTo("2026-11-23T13:00:00Z"));

        clock.set(Instant.parse("2026-11-23T13:00:00Z"));
        runDue();
        assertThat(finder.calls).hasSize(1);
    }

    @Test
    void aRunMissedWhileOctoWasDownOutsideTheWindowWaitsForTheWindow() {
        String body = ps5Search().replace("\"intervalMinutes\": 5",
                "\"intervalMinutes\": 5, \"window\": {\"from\": \"11:00\", \"to\": \"17:00\"}");
        String id = id(post("/api/searches", body));
        runDue();

        // Octo comes back in the evening: the stored next run has passed, but the window is closed
        clock.set(Instant.parse("2026-11-23T19:00:00Z"));
        runDue();

        assertThat(finder.calls).hasSize(1);
        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.nextRun", v -> v.assertThat().isEqualTo("2026-11-24T10:00:00Z"));
    }

    @Test
    void aResumedSearchIsDueAtOnce() {
        String id = id(post("/api/searches", ps5Search().replace("\"intervalMinutes\": 5", "\"intervalMinutes\": 60")));
        runDue();
        post("/api/searches/" + id + "/pause", "");

        clock.advance(Duration.ofMinutes(1));
        post("/api/searches/" + id + "/resume", "");
        runDue();

        assertThat(finder.calls).hasSize(2);
    }

    @Test
    void anEditedScheduleAppliesFromTheEndOfTheLastRun() {
        String id = id(post("/api/searches", ps5Search().replace("\"intervalMinutes\": 5", "\"intervalMinutes\": 60")));
        runDue();

        put("/api/searches/" + id, ps5Search().replace("\"intervalMinutes\": 5", "\"intervalMinutes\": 10"));

        assertThat(get("/api/searches/" + id)).bodyJson()
                .hasPathSatisfying("$.nextRun", v -> v.assertThat().isEqualTo("2026-11-23T11:10:00Z"));
    }

    @Test
    void aSlowSearchDoesNotDelayOthersAndNeverOverlapsItself() throws InterruptedException {
        post("/api/searches", ps5Search());
        post("/api/searches", ps5Search().replace("\"query\": \"ps5\"", "\"query\": \"slow\""));
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        CountDownLatch release = finder.blockOn("slow");

        List<Thread> first = runner.startDue();
        assertThat(first).hasSize(2);
        awaitAlert();
        assertThat(notifier.sentTitles()).containsExactly("PS5 Disc");

        // the fast Search is due again; the slow one is still running and is not started twice
        clock.advance(Duration.ofMinutes(5));
        joinAll(runner.startDue());
        assertThat(finder.calls).containsExactlyInAnyOrder("ps5", "slow", "ps5");

        release.countDown();
        joinAll(first);
    }

    // --- Listing History -----------------------------------------------------------------------

    @Test
    void theListingHistoryIsNewestFirstWithDecisionReasonAndDetails() {
        String withEvaluator = ps5Search().replace("\"notifiers\":",
                "\"evaluator\": {\"plugin\": \"fake-evaluator\", \"settings\": {}}, \"notifiers\":");
        String id = id(post("/api/searches", withEvaluator));
        finder.offers.put("ps5", List.of(listing("PS5 defekt", 100_00)));
        runDue();
        finder.offers.put("ps5", List.of(listing("PS5 Disc", 240_00)));
        clock.advance(Duration.ofMinutes(5));
        runDue();

        assertThat(get("/api/searches/" + id + "/history")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[*].listing.title", v -> v.assertThat().asList().containsExactly("PS5 Disc", "PS5 defekt"))
                .hasPathSatisfying("$[0].listing.priceInCents", v -> v.assertThat().isEqualTo(240_00))
                .hasPathSatisfying("$[0].verdict.decision", v -> v.assertThat().isEqualTo("BUY"))
                .hasPathSatisfying("$[0].verdict.reason", v -> v.assertThat().isEqualTo("Marge reicht"))
                .hasPathSatisfying("$[0].verdict.details", v -> v.assertThat()
                        .isEqualTo(List.of(Map.of("label", "Marge", "value", "50 €"))))
                .hasPathSatisfying("$[1].verdict.decision", v -> v.assertThat().isEqualTo("NO_BUY"))
                .hasPathSatisfying("$[1].verdict.reason", v -> v.assertThat().asString().contains("defekt"));
    }

    @Test
    void theListingHistoryShowsAttributesAsTheListingTypeLabelsThem() {
        String id = id(post("/api/searches", ps5Search().replace("\"listingType\": \"generic\"", "\"listingType\": \"phone\"")));
        finder.offers.put("ps5", List.of(listing("iPhone 15 128GB", 240_00)));
        runDue();

        assertThat(get("/api/searches/" + id + "/history")).hasStatusOk().bodyJson()
                .hasPathSatisfying("$[0].display", v -> v.assertThat()
                        .isEqualTo(List.of(Map.of("label", "Speicher", "value", "128 GB"))))
                .hasPathSatisfying("$[0].listing.attributes.storage", v -> v.assertThat().isEqualTo("GB_128"));
    }

    @Test
    void theListingHistoryOfAnUnknownSearchIsNotFound() {
        assertThat(get("/api/searches/nope/history")).hasStatus(HttpStatus.NOT_FOUND);
    }

    // --- helpers -------------------------------------------------------------------------------

    /** One scheduler tick, waiting until the Searches it started have finished. */
    private void runDue() {
        joinAll(runner.startDue());
    }

    private static void joinAll(List<Thread> threads) {
        for (Thread thread : threads) {
            try {
                assertThat(thread.join(Duration.ofSeconds(10))).as("run finished").isTrue();
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }
    }

    private void awaitAlert() throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (notifier.sent.isEmpty()) {
            assertThat(System.nanoTime()).as("waiting for an Alert").isLessThan(deadline);
            Thread.sleep(10);
        }
    }

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
                  "schedule": {"intervalMinutes": 5},
                  "retention": {"newest": 50}
                }""".formatted(quoted(decisions));
    }

    private static String quoted(String decisions) {
        return decisions.isBlank() ? "" : "\"" + String.join("\",\"", decisions.split(",")) + "\"";
    }

    /** Logs in like the frontend does and returns the session. */
    private MockHttpSession login() {
        return (MockHttpSession) mvc.post().uri("/api/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(USER, PASSWORD))
                .exchange().getRequest().getSession(false);
    }

    private MvcTestResult get(String uri) {
        return mvc.get().uri(uri).session(session).exchange();
    }

    private MvcTestResult post(String uri, String body) {
        return mvc.post().uri(uri).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult put(String uri, String body) {
        return mvc.put().uri(uri).session(session)
                .contentType(MediaType.APPLICATION_JSON).content(body).exchange();
    }

    private MvcTestResult delete(String uri) {
        return mvc.delete().uri(uri).session(session).exchange();
    }

    private static String id(MvcTestResult result) {
        String body = new String(result.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
        return tools.jackson.databind.json.JsonMapper.shared().readTree(body).get("id").asString();
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
        FakeEvaluator fakeEvaluator() {
            return new FakeEvaluator();
        }

        @Bean
        RecordingNotifier recordingNotifier() {
            return new RecordingNotifier();
        }
    }

    record FakeFinderSettings(@NotBlank String query) {
    }

    /** Offers, per query, the Listings a test put there, and records the queries it was asked. */
    static class FakeFinder implements Finder<FakeFinderSettings> {
        final Map<String, List<Listing>> offers = new ConcurrentHashMap<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        private final Map<String, CountDownLatch> blocked = new ConcurrentHashMap<>();

        void reset() {
            offers.clear();
            calls.clear();
            blocked.clear();
        }

        /** Finding for this query waits until the returned latch is released. */
        CountDownLatch blockOn(String query) {
            CountDownLatch latch = new CountDownLatch(1);
            blocked.put(query, latch);
            return latch;
        }

        @Override
        public Result find(FakeFinderSettings settings, ListingType<?> listingType) {
            calls.add(settings.query());
            CountDownLatch latch = blocked.get(settings.query());
            if (latch != null) {
                try {
                    latch.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
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

    record FakeEvaluatorSettings() {
    }

    /** Buys everything and says why, with one evaluation detail. */
    static class FakeEvaluator implements Evaluator<FakeEvaluatorSettings> {
        @Override
        public Verdict evaluate(Listing listing, FakeEvaluatorSettings settings) {
            return new Verdict(Decision.BUY, "Marge reicht", List.of(new LabelledValue("Marge", "50 €")));
        }

        @Override
        public String id() {
            return "fake-evaluator";
        }

        @Override
        public Set<String> listingTypes() {
            return Set.of(GenericListingType.ID);
        }

        @Override
        public Class<FakeEvaluatorSettings> settingsType() {
            return FakeEvaluatorSettings.class;
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

    // --- the clock -------------------------------------------------------------------------------

    @TestConfiguration(proxyBeanMethods = false)
    static class TestClock {
        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(MONDAY_NOON, ZoneId.of("Europe/Berlin"));
        }
    }

    /** A clock in the global timezone that tests set and advance. */
    static class MutableClock extends Clock {
        private volatile Instant now;
        private final ZoneId zone;

        MutableClock(Instant now, ZoneId zone) {
            this.now = now;
            this.zone = zone;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
