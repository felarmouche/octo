package com.felarmouche.octo.core.shared;

import lombok.Getter;

@Getter
public class PluginException extends RuntimeException {
    private final String pluginId;
    public PluginException(String message, String pluginId) {
        super(message);
        this.pluginId = pluginId;
    }
}
