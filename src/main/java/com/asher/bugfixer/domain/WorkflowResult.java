package com.asher.bugfixer.domain;

import java.util.List;

public record WorkflowResult(Status status, String message, List<String> notes) {
    public enum Status {
        COMPLETED_DRY_RUN,
        SKIPPED,
        FAILED
    }
}
