package com.asher.bugfixer.http;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.asher.bugfixer.domain.BugFixRequest;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class JiraWebhookParserTest {
    @Test
    void extractsOnlyTheEventFieldsNeededForQueueing() throws Exception {
        String body = """
                {
                  "webhookEvent":"jira:issue_updated",
                  "issue":{"id":"10001","key":"PAY-123","fields":{"summary":"Payment bug"}},
                  "changelog":{"items":[{"field":"status","toString":"Ready for Agent"}]}
                }
                """;
        BugFixRequest request = new JiraWebhookParser().parse("delivery-1", body.getBytes(StandardCharsets.UTF_8));
        assertEquals("PAY-123", request.issueKey());
        assertEquals("Ready for Agent", request.targetStatus());
    }
}
