package com.asher.bugfixer.opencode;

public record FixResult(boolean attempted, boolean succeeded, int exitCode, String output) {
    public static FixResult disabled() {
        return new FixResult(false, false, -1, "OpenCode execution is disabled.");
    }
}
