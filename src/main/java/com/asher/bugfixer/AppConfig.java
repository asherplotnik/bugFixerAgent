package com.asher.bugfixer;

import com.asher.bugfixer.validation.ValidationProfile;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

/**
 * Trusted deployment configuration. Jira payloads must never select a repository,
 * command, model, or executable.
 */
public record AppConfig(
        int maxWebhookBytes,
        String jiraWebhookSecret,
        String jiraBaseUrl,
        String jiraUserEmail,
        String jiraApiToken,
        boolean workerEnabled,
        String agentReadyStatus,
        Path targetRepository,
        String targetRepositoryName,
        String targetBranch,
        boolean opencodeEnabled,
        String opencodeBinary,
        String opencodeModel,
        Path opencodeConfig,
        int maxFixAttempts,
        boolean adkEnabled,
        String adkModel,
        ValidationProfile validationProfile,
        Duration opencodeTimeout,
        Duration validationTimeout,
        Path workspaceRoot) {

    public static AppConfig fromEnvironment() {
        Map<String, String> environment = System.getenv();
        Path appRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        return new AppConfig(
                integer(environment, "MAX_WEBHOOK_BYTES", 1_048_576, 1, 10_485_760),
                required(environment, "JIRA_WEBHOOK_SECRET"),
                optional(environment, "JIRA_BASE_URL"),
                optional(environment, "JIRA_USER_EMAIL"),
                optional(environment, "JIRA_API_TOKEN"),
                bool(environment, "WORKER_ENABLED", false),
                environment.getOrDefault("AGENT_READY_STATUS", "Ready for Agent"),
                path(environment, "TARGET_REPOSITORY"),
                environment.getOrDefault("TARGET_REPOSITORY_NAME", "unconfigured-repository"),
                environment.getOrDefault("TARGET_BRANCH", "main"),
                bool(environment, "OPENCODE_ENABLED", false),
                environment.getOrDefault("OPENCODE_BINARY", "opencode"),
                environment.getOrDefault("OPENCODE_MODEL", "gemini-2.5-flash"),
                appRoot.resolve(environment.getOrDefault("OPENCODE_CONFIG", "runtime/opencode-automation.json")).normalize(),
                integer(environment, "MAX_FIX_ATTEMPTS", 3, 1, 5),
                bool(environment, "ADK_ENABLED", false),
                environment.getOrDefault("ADK_MODEL", "gemini-2.5-flash"),
                ValidationProfile.parse(environment.getOrDefault("VALIDATION_PROFILE", "NONE")),
                Duration.ofMinutes(integer(environment, "OPENCODE_TIMEOUT_MINUTES", 15, 1, 60)),
                Duration.ofMinutes(integer(environment, "VALIDATION_TIMEOUT_MINUTES", 20, 1, 90)),
                appRoot.resolve("runtime/work").normalize());
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set. Refusing to accept unsigned Jira webhooks.");
        }
        return value;
    }

    private static int integer(Map<String, String> environment, String name, int fallback, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(environment.getOrDefault(name, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be an integer", exception);
        }
        if (value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static boolean bool(Map<String, String> environment, String name, boolean fallback) {
        return Boolean.parseBoolean(environment.getOrDefault(name, Boolean.toString(fallback)));
    }

    private static String optional(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? null : value;
    }

    private static Path path(Map<String, String> environment, String name) {
        String value = environment.get(name);
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }
}
