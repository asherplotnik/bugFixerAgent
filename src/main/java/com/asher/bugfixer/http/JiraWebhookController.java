package com.asher.bugfixer.http;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.workflow.InMemoryRequestQueue;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.core.JacksonException;

/** Public webhook endpoint: validates and queues work; it never executes a fix inline. */
@RestController
@RequestMapping("/webhooks")
public final class JiraWebhookController {
    private static final Set<String> SUPPORTED_EVENTS = Set.of("jira:issue_updated");
    private final AppConfig config;
    private final InMemoryRequestQueue queue;
    private final WebhookSignatureVerifier verifier;
    private final JiraWebhookParser parser;
    private final DeliveryDeduplicator deduplicator;

    public JiraWebhookController(
            AppConfig config,
            InMemoryRequestQueue queue,
            WebhookSignatureVerifier verifier,
            JiraWebhookParser parser,
            DeliveryDeduplicator deduplicator) {
        this.config = config;
        this.queue = queue;
        this.verifier = verifier;
        this.parser = parser;
        this.deduplicator = deduplicator;
    }

    @PostMapping(value = "/jira", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> receive(
            @RequestHeader(value = "X-Hub-Signature", required = false) String signature,
            @RequestHeader(value = "X-Atlassian-Webhook-Identifier", required = false) String deliveryId,
            @RequestBody byte[] rawBody) {
        if (rawBody.length > config.maxWebhookBytes()) {
            return response(HttpStatus.CONTENT_TOO_LARGE, "error", "payload_too_large");
        }
        if (!verifier.isValid(rawBody, signature)) {
            return response(HttpStatus.UNAUTHORIZED, "error", "invalid_signature");
        }
        if (deliveryId == null || deliveryId.isBlank()) {
            return response(HttpStatus.BAD_REQUEST, "error", "missing_delivery_identifier");
        }
        if (deduplicator.alreadySeen(deliveryId)) {
            return response(HttpStatus.OK, "status", "duplicate");
        }

        try {
            BugFixRequest request = parser.parse(deliveryId, rawBody);
            if (!SUPPORTED_EVENTS.contains(request.eventType())) {
                return response(HttpStatus.ACCEPTED, "status", "ignored_event");
            }
            if (!config.agentReadyStatus().equals(request.targetStatus())) {
                return response(HttpStatus.ACCEPTED, "status", "ignored_transition");
            }
            queue.enqueue(request);
            return ResponseEntity.accepted().body(Map.of("status", "queued", "issueKey", request.issueKey()));
        } catch (JacksonException | IllegalArgumentException exception) {
            return response(HttpStatus.BAD_REQUEST, "error", "invalid_jira_payload");
        }
    }

    private ResponseEntity<Map<String, String>> response(HttpStatus status, String key, String value) {
        return ResponseEntity.status(status).body(Map.of(key, value));
    }
}
