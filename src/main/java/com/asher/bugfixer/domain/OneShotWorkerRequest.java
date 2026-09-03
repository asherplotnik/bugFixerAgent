package com.asher.bugfixer.domain;

import java.time.Instant;
import java.util.Map;

/** Builds the trusted, deployment-supplied task passed to one Job worker invocation. */
public final class OneShotWorkerRequest {
    private OneShotWorkerRequest() {
    }

    public static BugFixRequest from(Map<String, String> environment) {
        String issueKey = required(environment, "JOB_ISSUE_KEY");
        return new BugFixRequest(
                value(environment, "JOB_DELIVERY_ID", "job-" + issueKey),
                value(environment, "JOB_ISSUE_ID", issueKey),
                issueKey,
                "job:one_shot",
                value(environment, "JOB_ISSUE_SUMMARY", "One-shot bug-fix worker invocation"),
                "Ready for Agent",
                Instant.now());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set when WORKER_MODE is true.");
        }
        return value;
    }

    private static String value(Map<String, String> environment, String name, String fallback) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
