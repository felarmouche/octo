package com.felarmouche.octo.core.finder;

import com.felarmouche.octo.core.shared.PluginException;

public class FindException extends PluginException {
    public FindException(String message, String pluginId) {
        super(message, pluginId);
    }
}
