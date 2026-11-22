package com.felarmouche.octo.core.plugin;

import com.felarmouche.octo.core.domain.Alert;

import java.util.List;
import java.util.Set;

/**
 * A Plugin that sends Alerts over one channel. The run already filtered the Alerts
 * to the Decisions the notifier slot reports. Notifiers support every Listing Type.
 */
public interface Notifier<S extends Record> extends Plugin<S> {

    void send(List<Alert> alerts, S settings);

    /** Empty: a Notifier is not tied to any Listing Type, see {@link #supports(String)}. */
    @Override
    default Set<String> listingTypes() {
        return Set.of();
    }

    @Override
    default boolean supports(String listingTypeId) {
        return true;
    }
}
