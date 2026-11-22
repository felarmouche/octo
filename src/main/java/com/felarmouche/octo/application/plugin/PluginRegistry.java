package com.felarmouche.octo.application.plugin;

import com.felarmouche.octo.core.plugin.Plugin;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every Plugin bean, by id. Two Plugins with the same id stop the start. */
@Component
public class PluginRegistry {

    private final Map<String, Plugin<?>> byId;
    private final List<Plugin<?>> all;

    public PluginRegistry(List<Plugin<?>> plugins) {
        this.byId = plugins.stream().collect(Collectors.toUnmodifiableMap(Plugin::id, Function.identity()));
        this.all = plugins.stream()
                .sorted(Comparator.comparing((Plugin<?> p) -> PluginKind.of(p)).thenComparing(Plugin::id))
                .toList();
    }

    public Optional<Plugin<?>> find(String id) {
        return Optional.ofNullable(id).map(byId::get);
    }

    /** By kind in the order of the run, then by id. */
    public List<Plugin<?>> all() {
        return all;
    }

    /** The Plugins a Search of this Listing Type can use, ordered like {@link #all()}. */
    public List<Plugin<?>> supporting(String listingTypeId) {
        return all.stream().filter(p -> p.supports(listingTypeId)).toList();
    }
}
