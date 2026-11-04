package com.felarmouche.octo.core.evaluator;

import com.felarmouche.octo.core.shared.PluginException;
import lombok.Getter;

@Getter
public class EvaluatorException extends PluginException {
    public EvaluatorException(String message, String pluginId) {
        super(message, pluginId);
    }
}
