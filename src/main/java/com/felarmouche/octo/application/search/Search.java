package com.felarmouche.octo.application.search;

import com.felarmouche.octo.core.domain.Alert;
import com.felarmouche.octo.core.domain.Decision;
import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.domain.Verdict;
import com.felarmouche.octo.core.exclusion.ExclusionFilter;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A standing, user-defined hunt for one kind of item. Each slot pairs a Plugin with the
 * settings this Search holds for it, so the Plugin itself stays stateless.
 */
public record Search(
        String id,
        String name,
        TypeSlot<?> type,
        ExclusionFilterSettings exclusion,
        List<FinderSlot<?>> finders,
        Optional<EvaluatorSlot<?>> evaluator,
        List<NotifierSlot<?>> notifiers) {

    /** @throws IllegalArgumentException when a selected Exclusion Preset is not offered for the Listing Type */
    public Search {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        exclusion = exclusion == null ? ExclusionFilterSettings.NONE : exclusion;
        exclusionFilter(type, exclusion);
        finders = List.copyOf(finders);
        evaluator = evaluator == null ? Optional.empty() : evaluator;
        notifiers = List.copyOf(notifiers);
    }

    /** The Exclusion Filter of this Search, built once per run. */
    ExclusionFilter exclusionFilter() {
        return exclusionFilter(type, exclusion);
    }

    private static ExclusionFilter exclusionFilter(TypeSlot<?> type, ExclusionFilterSettings exclusion) {
        return ExclusionFilter.of(exclusion, type.listingType().availableExclusionPresets());
    }

    /** The Search's Listing Type and the settings of its Listing Type Filter. */
    public record TypeSlot<F extends Record>(ListingType<F> listingType, F filter) {
        public TypeSlot {
            Objects.requireNonNull(listingType, "listingType");
            Objects.requireNonNull(filter, "filter");
        }

        Listing complete(Listing listing) {
            return listingType.complete(listing);
        }

        Optional<String> reject(Listing listing) {
            return listingType.reject(listing, filter);
        }

        List<LabelledValue> describe(Listing listing) {
            return listingType.describe(listing);
        }
    }

    public record FinderSlot<S extends Record>(Finder<S> finder, S settings) {
        Finder.Result find(ListingType<?> listingType) {
            return finder.find(settings, listingType);
        }

        String pluginId() {
            return finder.id();
        }
    }

    public record EvaluatorSlot<S extends Record>(Evaluator<S> evaluator, S settings) {
        Verdict evaluate(Listing listing) {
            return evaluator.evaluate(listing, settings);
        }
    }

    /** A Notifier, the Decisions it reports, and its settings. */
    public record NotifierSlot<S extends Record>(Notifier<S> notifier, Set<Decision> decisions, S settings) {
        public NotifierSlot {
            decisions = Set.copyOf(decisions);
        }

        void send(List<Alert> alerts) {
            notifier.send(alerts, settings);
        }

        String pluginId() {
            return notifier.id();
        }
    }
}
