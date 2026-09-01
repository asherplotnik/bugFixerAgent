package com.asher.bugfixer.validation;

public record ValidationResult(Status status, int exitCode, String output) {
    public enum Status {
        NOT_CONFIGURED,
        PASSED,
        FAILED,
        TIMED_OUT
    }

    public boolean passed() {
        return status == Status.PASSED;
    }
}
