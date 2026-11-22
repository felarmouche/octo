package com.felarmouche.octo.application.search;

import com.felarmouche.octo.application.plugin.ListingTypes;
import com.felarmouche.octo.application.plugin.PluginRegistry;
import com.felarmouche.octo.application.settings.InvalidSettingsException;
import com.felarmouche.octo.application.settings.SettingsCodec;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.core.plugin.Plugin;
import org.springframework.data.jdbc.core.JdbcAggregateTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates, edits, pauses, resumes and deletes stored Searches. Saving refuses unknown Plugins, Plugins of the
 * wrong kind or Listing Type, invalid settings or Schedule, and Searches without a Finder or a Notifier.
 * A Search is stored in its normalised form, so what is read back is what runs.
 *
 * It also plans when each Search runs next, in the timezone of the clock.
 */
@Service
public class SearchService {

    private final SearchRepository repository;
    private final JdbcAggregateTemplate aggregates;
    private final PluginRegistry plugins;
    private final ListingTypes listingTypes;
    private final SettingsCodec codec;
    private final SearchMemory memory;
    private final Clock clock;

    SearchService(SearchRepository repository, JdbcAggregateTemplate aggregates, PluginRegistry plugins,
                  ListingTypes listingTypes, SettingsCodec codec, SearchMemory memory, Clock clock) {
        this.repository = repository;
        this.aggregates = aggregates;
        this.plugins = plugins;
        this.listingTypes = listingTypes;
        this.codec = codec;
        this.memory = memory;
        this.clock = clock;
    }

    /**
     * The definition a new Search of this Listing Type starts from: no name, empty filters, no Plugins,
     * and the default Schedule and retention rule. With a name, a Finder and a Notifier it can be created.
     *
     * @throws InvalidSettingsException without a field when there is no Listing Type with this id
     */
    public SearchDefinition template(String listingType) {
        ListingType<?> type = listingTypes.find(listingType)
                .orElseThrow(() -> new InvalidSettingsException(null, "unknown Listing Type: " + listingType));
        return new SearchDefinition("", type.id(),
                // the filter as a saved Search stores it when none of its fields is set
                codec.encode(codec.decode(null, type.filterSettingsType(), "typeFilter")),
                codec.encode(ExclusionFilterSettings.NONE),
                List.of(), null, List.of(),
                codec.encode(Schedule.DEFAULT),
                codec.encode(Retention.DEFAULT));
    }

    /** A new Search is active and due at once, if it is inside its Schedule. */
    public StoredSearch create(SearchDefinition definition) {
        String id = UUID.randomUUID().toString();
        Search search = build(id, definition);
        Schedule schedule = schedule(definition);
        Instant now = clock.instant();
        StoredSearch stored = new StoredSearch(id, true, normalise(search, schedule), firstRun(schedule, now));
        aggregates.insert(SearchRow.of(stored, now));
        return stored;
    }

    /**
     * Applies from the next run, which the new Schedule plans from the end of the last run.
     * What the Search has seen and its Listing History stay.
     */
    public StoredSearch update(String id, SearchDefinition definition) {
        SearchRow row = row(id);
        if (!row.listingType().equals(definition.listingType()))
            throw new InvalidSettingsException("listingType",
                    "is fixed after creation; create a new Search for another Listing Type");
        Search search = build(id, definition);
        Schedule schedule = schedule(definition);
        Instant nextRun = memory.lastStatus(id)
                .map(last -> schedule.nextRunAfter(last.finishedAt(), clock.getZone()))
                .orElseGet(() -> firstRun(schedule, clock.instant()));
        return repository.save(row.with(normalise(search, schedule), nextRun)).toStored();
    }

    public StoredSearch pause(String id) {
        SearchRow row = row(id);
        return repository.save(row.withActive(false, row.nextRunAt())).toStored();
    }

    /** Due at once, if it is inside its Schedule. */
    public StoredSearch resume(String id) {
        SearchRow row = row(id);
        Instant nextRun = firstRun(schedule(row.toStored()), clock.instant());
        return repository.save(row.withActive(true, nextRun)).toStored();
    }

    /** The active Searches whose next run time has come. */
    public List<StoredSearch> due(Instant now) {
        return repository.findByActiveTrueAndNextRunAtLessThanEqualOrderByNextRunAtAscIdAsc(now).stream()
                .map(SearchRow::toStored).toList();
    }

    /** Sets the next run time. Does nothing when the Search was deleted meanwhile. */
    public void planRun(String id, Instant nextRun) {
        repository.planRun(id, nextRun);
    }

    /**
     * Plans the next run from the end of the previous one, with the Schedule as stored now,
     * so an edit saved during the run applies. Does nothing when the Search was deleted meanwhile.
     */
    public void planNextRun(String id, Instant previousEnd) {
        repository.findById(id).ifPresent(row ->
                planRun(id, schedule(row.toStored()).nextRunAfter(previousEnd, clock.getZone())));
    }

    /** @throws InvalidSettingsException when the stored Schedule can't be read */
    public Schedule schedule(StoredSearch stored) {
        return schedule(stored.definition());
    }

    private Schedule schedule(SearchDefinition d) {
        return codec.decode(d.schedule(), Schedule.class, "schedule");
    }

    private Instant firstRun(Schedule schedule, Instant now) {
        return schedule.firstRunFrom(now, clock.getZone());
    }

    /** Also deletes the Search's Listing History and seen Fingerprints. */
    public void delete(String id) {
        if (!repository.existsById(id)) throw new SearchNotFoundException(id);
        repository.deleteById(id);
    }

    public StoredSearch get(String id) {
        return row(id).toStored();
    }

    /** Empty when the Search was deleted. */
    public Optional<StoredSearch> find(String id) {
        return repository.findById(id).map(SearchRow::toStored);
    }

    /** The Search's Listing History, newest first. */
    public List<JudgedListing> history(String id) {
        if (!repository.existsById(id)) throw new SearchNotFoundException(id);
        return memory.history(id).reversed();
    }

    /** Oldest first. */
    public List<StoredSearch> list() {
        return repository.findAllByOrderByCreatedAtAscIdAsc().stream().map(SearchRow::toStored).toList();
    }

    private SearchRow row(String id) {
        return repository.findById(id).orElseThrow(() -> new SearchNotFoundException(id));
    }

    /**
     * The runnable Search: resolves the Plugins and decodes and validates every settings object.
     *
     * @throws InvalidSettingsException naming the offending field
     */
    public Search build(StoredSearch stored) {
        return build(stored.id(), stored.definition());
    }

    private Search build(String id, SearchDefinition d) {
        if (d.name() == null || d.name().isBlank()) throw new InvalidSettingsException("name", "must not be blank");
        ListingType<?> type = listingTypes.require(d.listingType());

        Search.TypeSlot<?> typeSlot = typeSlot(type, d.typeFilter());
        ExclusionFilterSettings exclusion = codec.decode(d.exclusion(), ExclusionFilterSettings.class, "exclusion");

        if (d.finders().isEmpty()) throw new InvalidSettingsException("finders", "a Search needs at least one Finder");
        List<Search.FinderSlot<?>> finders = new ArrayList<>();
        for (int i = 0; i < d.finders().size(); i++) {
            SearchDefinition.Slot slot = d.finders().get(i);
            String path = "finders[" + i + "]";
            finders.add(finderSlot((Finder<?>) plugin(slot.plugin(), Finder.class, type, path), slot.settings(), path + ".settings"));
        }

        Optional<Search.EvaluatorSlot<?>> evaluator = Optional.ofNullable(d.evaluator()).map(slot ->
                evaluatorSlot((Evaluator<?>) plugin(slot.plugin(), Evaluator.class, type, "evaluator"), slot.settings(), "evaluator.settings"));

        if (d.notifiers().isEmpty()) throw new InvalidSettingsException("notifiers", "a Search needs at least one Notifier");
        List<Search.NotifierSlot<?>> notifiers = new ArrayList<>();
        for (int i = 0; i < d.notifiers().size(); i++) {
            SearchDefinition.NotifierSlot slot = d.notifiers().get(i);
            String path = "notifiers[" + i + "]";
            Notifier<?> notifier = (Notifier<?>) plugin(slot.plugin(), Notifier.class, type, path);
            if (slot.decisions().isEmpty())
                throw new InvalidSettingsException(path + ".decisions", "must report at least one Decision");
            notifiers.add(notifierSlot(notifier, slot, path + ".settings"));
        }

        Retention retention = codec.decode(d.retention(), Retention.class, "retention");
        try {
            return new Search(id, d.name(), typeSlot, exclusion, finders, evaluator, notifiers, retention);
        } catch (IllegalArgumentException e) {
            throw new InvalidSettingsException("exclusion.presets", e.getMessage());
        }
    }

    /** The Plugin with this id, if it is of the given kind and supports the Listing Type. */
    private Plugin<?> plugin(String id, Class<?> kind, ListingType<?> type, String path) {
        String field = path + ".plugin";
        Plugin<?> plugin = plugins.find(id).orElseThrow(() -> new InvalidSettingsException(field, "unknown Plugin: " + id));
        if (!kind.isInstance(plugin))
            throw new InvalidSettingsException(field, id + " is not a " + kind.getSimpleName());
        if (!plugin.supports(type.id()))
            throw new InvalidSettingsException(field, id + " does not support the Listing Type " + type.id());
        return plugin;
    }

    private <F extends Record> Search.TypeSlot<F> typeSlot(ListingType<F> type, JsonNode json) {
        return new Search.TypeSlot<>(type, codec.decode(json, type.filterSettingsType(), "typeFilter"));
    }

    private <S extends Record> Search.FinderSlot<S> finderSlot(Finder<S> finder, JsonNode json, String path) {
        return new Search.FinderSlot<>(finder, codec.decode(json, finder.settingsType(), path));
    }

    private <S extends Record> Search.EvaluatorSlot<S> evaluatorSlot(Evaluator<S> evaluator, JsonNode json, String path) {
        return new Search.EvaluatorSlot<>(evaluator, codec.decode(json, evaluator.settingsType(), path));
    }

    private <S extends Record> Search.NotifierSlot<S> notifierSlot(Notifier<S> notifier, SearchDefinition.NotifierSlot slot,
                                                                   String path) {
        return new Search.NotifierSlot<>(notifier, slot.decisions(), codec.decode(slot.settings(), notifier.settingsType(), path));
    }

    /** The definition as stored: every settings object re-encoded from its typed record. */
    private SearchDefinition normalise(Search s, Schedule schedule) {
        return new SearchDefinition(
                s.name(),
                s.type().listingType().id(),
                codec.encode(s.type().filter()),
                codec.encode(s.exclusionSettings()),
                s.finders().stream().map(f -> new SearchDefinition.Slot(f.pluginId(), codec.encode(f.settings()))).toList(),
                s.evaluator().map(e -> new SearchDefinition.Slot(e.evaluator().id(), codec.encode(e.settings()))).orElse(null),
                s.notifiers().stream().map(n -> new SearchDefinition.NotifierSlot(
                        n.pluginId(), n.decisions(), codec.encode(n.settings()))).toList(),
                codec.encode(schedule),
                codec.encode(s.retention()));
    }
}
