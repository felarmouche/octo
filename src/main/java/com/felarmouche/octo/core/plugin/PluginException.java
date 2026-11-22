package com.felarmouche.octo.core.plugin;

/** The one exception type Plugins throw. It carries the id of the failing Plugin. */
public class PluginException extends RuntimeException {

    private final String pluginId;

    public PluginException(String pluginId, String message) {
        super(message);
        this.pluginId = pluginId;
    }

    public PluginException(String pluginId, String message, Throwable cause) {
        super(message, cause);
        this.pluginId = pluginId;
    }

    public String pluginId() {
        return pluginId;
    }
}
