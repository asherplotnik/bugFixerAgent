package com.asher.bugfixer.http;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class WebhookSignatureVerifierTest {
    @Test
    void verifiesAtlassianPublishedSha256Example() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("It's a Secret to Everybody");
        assertTrue(verifier.isValid(
                "Hello World!".getBytes(StandardCharsets.UTF_8),
                "sha256=a4771c39fbe90f317c7824e83ddef3caae9cb3d976c214ace1f2937e133263c9"));
    }

    @Test
    void rejectsMissingOrWrongSignature() {
        WebhookSignatureVerifier verifier = new WebhookSignatureVerifier("secret");
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertFalse(verifier.isValid(body, null));
        assertFalse(verifier.isValid(body, "sha256=deadbeef"));
    }
}
