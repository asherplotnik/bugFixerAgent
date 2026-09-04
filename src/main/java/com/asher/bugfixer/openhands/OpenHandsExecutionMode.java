package com.asher.bugfixer.openhands;

import java.util.Locale;

/** Selects the trusted runtime that executes the file-editor-only OpenHands worker. */
public enum OpenHandsExecutionMode {
    LOCAL,
    DOCKER,
    KUBERNETES;

    public static OpenHandsExecutionMode parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("OPENHANDS_EXECUTION_MODE must be LOCAL, DOCKER, or KUBERNETES.", exception);
        }
    }
}
