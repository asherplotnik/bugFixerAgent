package com.asher.bugfixer.http;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.openhands.OpenHandsProvider;
import com.asher.bugfixer.validation.ValidationProfile;
import com.asher.bugfixer.workflow.InMemoryRequestQueue;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class JiraWebhookControllerTest {
    @Test
    void acceptsAValidReadyForAgentDelivery() throws Exception {
        String secret = "test-secret";
        AppConfig config = new AppConfig(
                100_000, secret, null, null, null, false, "Ready for Agent", null,
                "test", "main", false, "opencode", "model", Path.of("runtime/opencode-automation.json"),
                false, "python", Path.of("runtime/openhands_worker.py"), OpenHandsProvider.GEMINI, "model", null,
                "https://api.groq.com/openai/v1", null,
                null, null, null, 8787,
                1, false, "gemini-2.5-flash", null, null, null, null, null, ValidationProfile.NONE, "npm", Duration.ofMinutes(1),
                Duration.ofMinutes(1), Duration.ofMinutes(1), Path.of("runtime/work"), false, false, null, "");
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new JiraWebhookController(
                config,
                new InMemoryRequestQueue(),
                new WebhookSignatureVerifier(secret),
                new JiraWebhookParser(),
                new DeliveryDeduplicator())).build();
        String body = """
                {"webhookEvent":"jira:issue_updated","issue":{"id":"10001","key":"PAY-123","fields":{"summary":"Payment bug"}},"changelog":{"items":[{"field":"status","toString":"Ready for Agent"}]}}
                """;

        mvc.perform(post("/webhooks/jira")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Hub-Signature", signature(secret, body))
                        .header("X-Atlassian-Webhook-Identifier", "delivery-1")
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("queued"))
                .andExpect(jsonPath("$.issueKey").value("PAY-123"));
    }

    private String signature(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
