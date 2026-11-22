package com.felarmouche.octo.application.api;

import com.felarmouche.octo.application.plugin.ListingTypes;
import com.felarmouche.octo.application.plugin.PluginKind;
import com.felarmouche.octo.application.plugin.PluginRegistry;
import com.felarmouche.octo.application.search.Retention;
import com.felarmouche.octo.application.search.Schedule;
import com.felarmouche.octo.application.search.SearchDefinition;
import com.felarmouche.octo.application.settings.InvalidSettingsException;
import com.felarmouche.octo.application.settings.SettingsSchema;
import com.felarmouche.octo.core.exclusion.ExclusionFilterSettings;
import com.felarmouche.octo.core.exclusion.ExclusionPreset;
import com.felarmouche.octo.core.listingtype.ListingType;
import com.felarmouche.octo.core.plugin.Plugin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * What a frontend needs to render the forms of a Search: Listing Types and Plugins with their settings schemas,
 * and the schemas of the fixed parts every Search has.
 */
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
     * @param exclusionSchema      the schema of the Exclusion Filter, offering exactly the presets of this Listing Type
     * @param exclusionPresets     the presets a Search of this Listing Type can select
     */
    record ListingTypeResponse(String id, String title, JsonNode filterSettingsSchema, JsonNode exclusionSchema,
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
        List<ExclusionPreset> available = type.availableExclusionPresets();
        List<PresetResponse> presets = available.stream().map(p -> new PresetResponse(p.id(), p.name())).toList();
        return new ListingTypeResponse(type.id(), type.title(), schema.of(type.filterSettingsType()),
                exclusionSchema(available), presets);
    }

    /** {@code presets} as a set of {@code oneOf} {@code {const, title}}, one per available preset. */
    private JsonNode exclusionSchema(List<ExclusionPreset> available) {
        ObjectNode exclusion = (ObjectNode) schema.of(ExclusionFilterSettings.class).deepCopy();
        ObjectNode presets = (ObjectNode) exclusion.get("properties").get("presets");
        presets.put("uniqueItems", true);
        ObjectNode items = presets.objectNode().put("type", "string");
        ArrayNode values = items.putArray("oneOf");
        available.forEach(p -> values.addObject().put("const", p.id()).put("title", p.name()));
        presets.set("items", items);
        return exclusion;
    }

    /** @param listingTypes the supported Listing Types; empty for Notifiers, which support every one */
    record PluginResponse(String id, String title, PluginKind kind, List<String> listingTypes, JsonNode settingsSchema) {
    }

    /**
     * @param listingType when given, only the Plugins a Search of this Listing Type can use
     * @throws InvalidSettingsException without a field when there is no Listing Type with this id
     */
    @GetMapping("/plugins")
    List<PluginResponse> plugins(@RequestParam(required = false) String listingType) {
        List<Plugin<?>> found = listingType == null ? plugins.all()
                : plugins.supporting(listingTypes.find(listingType)
                .orElseThrow(() -> new InvalidSettingsException(null, "unknown Listing Type: " + listingType)).id());
        return found.stream().map(this::response).toList();
    }

    private PluginResponse response(Plugin<?> plugin) {
        return new PluginResponse(plugin.id(), plugin.title(), PluginKind.of(plugin), plugin.listingTypes().stream().sorted().toList(),
                schema.of(plugin.settingsType()));
    }

    /**
     * The schemas of the parts of a Search that no Plugin or Listing Type brings.
     *
     * @param notifierDecisions the Decisions one Notifier reports
     */
    record SearchSchemaResponse(JsonNode schedule, JsonNode retention, JsonNode notifierDecisions) {
    }

    @GetMapping("/search-schema")
    SearchSchemaResponse searchSchema() {
        JsonNode notifierDecisions = schema.of(SearchDefinition.NotifierSlot.class).get("properties").get("decisions");
        return new SearchSchemaResponse(schema.of(Schedule.class), schema.of(Retention.class), notifierDecisions);
    }
}
