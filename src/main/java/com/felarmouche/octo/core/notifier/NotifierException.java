package com.felarmouche.octo.core.notifier;

import com.felarmouche.octo.core.shared.PluginException;

public class NotifierException extends PluginException {
    public NotifierException(String message, String pluginId) {
        super(message, pluginId);
    }
}
