package com.asher.bugfixer.http;

import com.asher.bugfixer.domain.BugFixRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;

/** Parses only the event fields needed to queue work. Jira text remains untrusted. */
public final class JiraWebhookParser {
    private final ObjectMapper mapper = new ObjectMapper();

    public BugFixRequest parse(String deliveryId, byte[] rawBody) throws IOException {
        JsonNode root = mapper.readTree(rawBody);
        String event = text(root, "webhookEvent");
        JsonNode issue = root.path("issue");
        String issueId = text(issue, "id");
        String issueKey = text(issue, "key");
        String summary = text(issue.path("fields"), "summary");
        String targetStatus = changedStatus(root.path("changelog"));

        if (event.isBlank() || issueId.isBlank() || issueKey.isBlank()) {
            throw new IllegalArgumentException("Jira webhook is missing webhookEvent, issue.id, or issue.key");
        }
        return new BugFixRequest(deliveryId, issueId, issueKey, event, summary, targetStatus, Instant.now());
    }

    private String changedStatus(JsonNode changelog) {
        for (JsonNode item : changelog.path("items")) {
            if ("status".equalsIgnoreCase(text(item, "field"))) {
                return text(item, "toString");
            }
        }
        return "";
    }

    private String text(JsonNode node, String name) {
        JsonNode value = node.path(name);
        return value.isTextual() ? value.textValue() : "";
    }
}
