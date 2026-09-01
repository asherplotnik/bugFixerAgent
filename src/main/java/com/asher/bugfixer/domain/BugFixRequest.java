package com.asher.bugfixer.domain;

import java.time.Instant;

/** Minimal, untrusted event data. The worker must re-read Jira before acting. */
public record BugFixRequest(
        String deliveryId,
        String issueId,
        String issueKey,
        String eventType,
        String summary,
        String targetStatus,
        Instant receivedAt) {
}
