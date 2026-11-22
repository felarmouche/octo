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
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Creates, edits, pauses, resumes and deletes stored Searches. Saving refuses unknown Plugins, Plugins of the
 * wrong kind or Listing Type, invalid settings, and Searches without a Finder or a Notifier.
 * A Search is stored in its normalised form, so what is read back is what runs.
 */
@Service
public class SearchService {

    private final JdbcSearchStore store;
    private final PluginRegistry plugins;
    private final ListingTypes listingTypes;
    private final SettingsCodec codec;

    public SearchService(JdbcSearchStore store, PluginRegistry plugins, ListingTypes listingTypes, SettingsCodec codec) {
        this.store = store;
        this.plugins = plugins;
        this.listingTypes = listingTypes;
        this.codec = codec;
    }

    /** A new Search is active. */
    public StoredSearch create(SearchDefinition definition) {
        String id = UUID.randomUUID().toString();
        StoredSearch search = new StoredSearch(id, true, normalise(build(id, definition)));
        store.insert(search);
        return search;
    }

    /** Applies from the next run. What the Search has seen and its Listing History stay. */
    public StoredSearch update(String id, SearchDefinition definition) {
        StoredSearch stored = get(id);
        if (!stored.definition().listingType().equals(definition.listingType()))
            throw new InvalidSettingsException("listingType",
                    "is fixed after creation; create a new Search for another Listing Type");
        SearchDefinition normalised = normalise(build(id, definition));
        if (!store.update(id, normalised)) throw new SearchNotFoundException(id);
        return new StoredSearch(id, stored.active(), normalised);
    }

    public StoredSearch pause(String id) {
        return setActive(id, false);
    }

    public StoredSearch resume(String id) {
        return setActive(id, true);
    }

    /** Also deletes the Search's Listing History and seen Fingerprints. */
    public void delete(String id) {
        if (!store.delete(id)) throw new SearchNotFoundException(id);
    }

    public StoredSearch get(String id) {
        return store.find(id).orElseThrow(() -> new SearchNotFoundException(id));
    }

    public List<StoredSearch> list() {
        return store.all();
    }

    public List<StoredSearch> active() {
        return store.active();
    }

    private StoredSearch setActive(String id, boolean active) {
        if (!store.setActive(id, active)) throw new SearchNotFoundException(id);
        return get(id);
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
        ListingType<?> type = listingTypes.find(d.listingType())
                .orElseThrow(() -> new InvalidSettingsException("listingType", "unknown Listing Type: " + d.listingType()));

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
    private SearchDefinition normalise(Search s) {
        return new SearchDefinition(
                s.name(),
                s.type().listingType().id(),
                codec.encode(s.type().filter()),
                codec.encode(s.exclusionSettings()),
                s.finders().stream().map(f -> new SearchDefinition.Slot(f.pluginId(), codec.encode(f.settings()))).toList(),
                s.evaluator().map(e -> new SearchDefinition.Slot(e.evaluator().id(), codec.encode(e.settings()))).orElse(null),
                s.notifiers().stream().map(n -> new SearchDefinition.NotifierSlot(
                        n.pluginId(), n.decisions(), codec.encode(n.settings()))).toList(),
                codec.encode(s.retention()));
    }
}
