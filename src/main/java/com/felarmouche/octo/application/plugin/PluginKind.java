package com.felarmouche.octo.application.plugin;

import com.felarmouche.octo.core.plugin.Evaluator;
import com.felarmouche.octo.core.plugin.Finder;
import com.felarmouche.octo.core.plugin.Notifier;
import com.felarmouche.octo.core.plugin.Plugin;

import java.util.Arrays;

/** Which slot of a Search a Plugin fills, in the order of the run. */
public enum PluginKind {
    FINDER(Finder.class),
    EVALUATOR(Evaluator.class),
    NOTIFIER(Notifier.class);

    private final Class<?> contract;

    PluginKind(Class<?> contract) {
        this.contract = contract;
    }

    public static PluginKind of(Plugin<?> plugin) {
        return Arrays.stream(values()).filter(k -> k.contract.isInstance(plugin)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(plugin.id() + " is no Finder, Evaluator or Notifier"));
    }
}
