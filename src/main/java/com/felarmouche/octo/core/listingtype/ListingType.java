package com.felarmouche.octo.core.listingtype;

import com.felarmouche.octo.core.domain.LabelledValue;
import com.felarmouche.octo.core.domain.Listing;
import com.felarmouche.octo.core.exclusion.ExclusionPreset;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The category of item a Listing describes. Each Search has exactly one.
 *
 * @param <F> the settings record of its Listing Type Filter; all its fields are optional
 */
public interface ListingType<F extends Record> {

    String id();

    Class<F> filterSettingsType();

    /** Fills unknown attributes from title and description. Applied to every found Listing. */
    Listing complete(Listing listing);

    /**
     * The Listing Type Filter: the reason when the Listing is known to be bad, empty when it passes.
     * Unknown values pass.
     */
    Optional<String> reject(Listing listing, F filterSettings);

    /** The Listing's attributes as labelled values for Alerts. */
    List<LabelledValue> describe(Listing listing);

    /** The Exclusion Presets this Listing Type offers, besides the common ones. */
    List<ExclusionPreset> exclusionPresets();

    /** The presets a Search of this Listing Type can select: the common ones, then its own. */
    default List<ExclusionPreset> availableExclusionPresets() {
        List<ExclusionPreset> available = new ArrayList<>(ExclusionPreset.COMMON);
        available.addAll(exclusionPresets());
        return List.copyOf(available);
    }
}
