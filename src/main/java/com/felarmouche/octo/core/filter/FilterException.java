package com.felarmouche.octo.core.filter;

import com.felarmouche.octo.core.shared.PluginException;

public class FilterException extends PluginException {
    public FilterException(String message, String pluginId) {
        super(message, pluginId);
    }
}
