package com.asher.bugfixer.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class OneShotWorkerRequestTest {
    @Test
    void requiresTheJiraIssueKey() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> OneShotWorkerRequest.from(Map.of()));

        assertEquals("JOB_ISSUE_KEY must be set when WORKER_MODE is true.", exception.getMessage());
    }

    @Test
    void usesTrustedJobInputsAndDefaults() {
        BugFixRequest request = OneShotWorkerRequest.from(Map.of(
                "JOB_ISSUE_KEY", "BUG-42",
                "JOB_ISSUE_SUMMARY", "Fix the payment failure"));

        assertEquals("BUG-42", request.issueKey());
        assertEquals("BUG-42", request.issueId());
        assertEquals("job-BUG-42", request.deliveryId());
        assertEquals("Fix the payment failure", request.summary());
    }
}
