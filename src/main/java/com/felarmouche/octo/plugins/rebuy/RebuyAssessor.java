package com.felarmouche.octo.plugins.rebuy;

import com.felarmouche.octo.core.domain.Listing;

/** Assesses a phone's grade, colour, storage and battery from its photos and text. */
interface RebuyAssessor {

    /** @throws RuntimeException when no photo can be loaded or no AI model answers */
    RebuyAssessment assess(Listing listing, RebuyCatalog catalog, int maxPhotos);
}
