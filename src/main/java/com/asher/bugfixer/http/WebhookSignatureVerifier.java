package com.asher.bugfixer.http;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/** Validates Jira Cloud's X-Hub-Signature without parsing or changing the raw body. */
public final class WebhookSignatureVerifier {
    private final byte[] secret;

    public WebhookSignatureVerifier(String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public boolean isValid(byte[] body, String providedSignature) {
        if (providedSignature == null || !providedSignature.startsWith("sha256=")) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            String expected = "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(body));
            return MessageDigest.isEqual(
                    expected.getBytes(StandardCharsets.US_ASCII),
                    providedSignature.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not calculate webhook signature", exception);
        }
    }
}
