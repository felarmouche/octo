package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.plugin.ListingTypes;
import com.felarmouche.octo.application.plugin.PluginKind;
import com.felarmouche.octo.application.plugin.PluginRegistry;
import com.felarmouche.octo.application.settings.SettingsSchema;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Plugin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** What a frontend needs to render the forms of a Search: Listing Types and Plugins with their settings schemas. */
@RestController
@RequestMapping("/api")
class DiscoveryController {

    private final ListingTypes listingTypes;
    private final PluginRegistry plugins;
    private final SettingsSchema schema;

    DiscoveryController(ListingTypes listingTypes, PluginRegistry plugins, SettingsSchema schema) {
        this.listingTypes = listingTypes;
        this.plugins = plugins;
        this.schema = schema;
    }

    /**
     * @param filterSettingsSchema the schema of its Listing Type Filter
     * @param exclusionSchema      the schema of the Exclusion Filter, the same for every Listing Type
     * @param exclusionPresets     the presets a Search of this Listing Type can select
     */
    record ListingTypeResponse(String id, JsonNode filterSettingsSchema, JsonNode exclusionSchema,
                               List<PresetResponse> exclusionPresets) {
    }

    record PresetResponse(String id, String name) {
    }

    /** In the order a frontend offers them. */
    @GetMapping("/listing-types")
    List<ListingTypeResponse> listingTypes() {
        return listingTypes.all().stream().map(this::response).toList();
    }

    private ListingTypeResponse response(ListingType<?> type) {
        List<PresetResponse> presets = type.availableExclusionPresets().stream()
                .map(p -> new PresetResponse(p.id(), p.name()))
                .toList();
        return new ListingTypeResponse(type.id(), schema.of(type.filterSettingsType()),
                schema.of(ExclusionFilterSettings.class), presets);
    }

    /** @param listingTypes the supported Listing Types; empty for Notifiers, which support every one */
    record PluginResponse(String id, PluginKind kind, List<String> listingTypes, JsonNode settingsSchema) {
    }

    /** @param listingType when given, only the Plugins a Search of this Listing Type can use */
    @GetMapping("/plugins")
    List<PluginResponse> plugins(@RequestParam(required = false) String listingType) {
        List<Plugin<?>> found = listingType == null ? plugins.all()
                : plugins.supporting(listingTypes.require(listingType).id());
        return found.stream().map(this::response).toList();
    }

    private PluginResponse response(Plugin<?> plugin) {
        return new PluginResponse(plugin.id(), PluginKind.of(plugin), plugin.listingTypes().stream().sorted().toList(),
                schema.of(plugin.settingsType()));
    }
}
