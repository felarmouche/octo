package com.felarmouche.octo.application.plugin;

import com.felarmouche.octo.core.plugin.Plugin;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Every Plugin bean, by id. Two Plugins with the same id stop the start. */
@Component
public class PluginRegistry {

    private final Map<String, Plugin<?>> byId;

    public PluginRegistry(List<Plugin<?>> plugins) {
        this.byId = plugins.stream().collect(Collectors.toUnmodifiableMap(Plugin::id, Function.identity()));
    }

    public Optional<Plugin<?>> find(String id) {
        return Optional.ofNullable(id).map(byId::get);
    }
}
