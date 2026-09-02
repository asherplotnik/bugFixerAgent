package com.asher.bugfixer.openhands;

import java.util.Locale;

/** Trusted model-provider choices for the OpenHands worker. */
public enum OpenHandsProvider {
    GEMINI,
    GROQ;

    public static OpenHandsProvider parse(String value) {
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (Exception exception) {
            throw new IllegalStateException("OPENHANDS_PROVIDER must be GEMINI or GROQ.", exception);
        }
    }
}
