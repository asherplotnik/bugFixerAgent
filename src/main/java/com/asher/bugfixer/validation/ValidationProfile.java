package com.asher.bugfixer.validation;

import java.util.List;

/** Trusted command profiles. No user, Jira, or model-provided command is accepted. */
public enum ValidationProfile {
    NONE,
    MAVEN_VERIFY,
    GRADLE_CHECK;

    public static ValidationProfile parse(String raw) {
        try {
            return valueOf(raw.trim().toUpperCase());
        } catch (Exception exception) {
            throw new IllegalStateException("Unsupported VALIDATION_PROFILE: " + raw);
        }
    }

    public List<String> command() {
        return switch (this) {
            case NONE -> List.of();
            case MAVEN_VERIFY -> List.of("./mvnw", "--batch-mode", "--no-transfer-progress", "verify");
            case GRADLE_CHECK -> List.of("./gradlew", "--no-daemon", "check");
        };
    }
}
