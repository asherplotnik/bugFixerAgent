package com.asher.bugfixer.openhands;

/** Result from one pre-authorized OpenHands execution. */
public record FixResult(boolean invoked, boolean succeeded, int exitCode, String output) {
    public static FixResult disabled() {
        return new FixResult(false, false, -1, "OpenHands execution is disabled.");
    }
}
