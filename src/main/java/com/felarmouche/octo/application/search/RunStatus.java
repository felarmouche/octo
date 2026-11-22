package com.felarmouche.octo.application.search;

import java.time.Instant;
import java.util.List;

/** The outcome of one run: when it ended, whether it was ok, and the failure messages. */
public record RunStatus(Instant finishedAt, boolean ok, List<String> messages) {
    public RunStatus {
        messages = List.copyOf(messages);
    }
}
