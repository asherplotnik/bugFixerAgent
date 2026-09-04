package com.asher.bugfixer;

import com.asher.bugfixer.validation.ValidationProfile;
import com.asher.bugfixer.openhands.OpenHandsProvider;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import org.springframework.core.env.Environment;

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
        boolean openhandsEnabled,
        boolean openhandsContainerEnabled,
        String openhandsContainerImage,
        String openhandsPythonBinary,
        Path openhandsWorkerScript,
        OpenHandsProvider openhandsProvider,
        String openhandsModel,
        String openhandsGeminiApiKey,
        String groqBaseUrl,
        String groqApiKey,
        String geminiConnectorBaseUrl,
        String geminiVertexProject,
        String geminiVertexLocation,
        int geminiProxyPort,
        int maxFixAttempts,
        boolean adkEnabled,
        String adkModel,
        String adkApiKey,
        String adkBaseUrl,
        String adkAuthorization,
        String adkClientId,
        String adkClientSecret,
        ValidationProfile validationProfile,
        String npmBinary,
        Duration openhandsTimeout,
        Duration validationTimeout,
        Path workspaceRoot,
        boolean localSimulationEnabled,
        boolean publishingEnabled,
        String githubToken,
        String githubRepository,
        String bitbucketBaseUrl,
        String bitbucketProjectKey,
        String bitbucketToken,
        Path investigationKnowledgeFile) {

    public static AppConfig from(Environment environment) {
        Path appRoot = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        boolean oneShotWorker = bool(environment, "WORKER_MODE", false);
        return new AppConfig(
                integer(environment, "MAX_WEBHOOK_BYTES", 1_048_576, 1, 10_485_760),
                oneShotWorker
                        ? value(environment, "JIRA_WEBHOOK_SECRET", "worker-mode-not-a-webhook-secret")
                        : required(environment, "JIRA_WEBHOOK_SECRET"),
                optional(environment, "JIRA_BASE_URL"),
                optional(environment, "JIRA_USER_EMAIL"),
                optional(environment, "JIRA_API_TOKEN"),
                bool(environment, "WORKER_ENABLED", false),
                value(environment, "AGENT_READY_STATUS", "Ready for Agent"),
                path(environment, "TARGET_REPOSITORY"),
                value(environment, "TARGET_REPOSITORY_NAME", "unconfigured-repository"),
                value(environment, "TARGET_BRANCH", "main"),
                bool(environment, "OPENHANDS_ENABLED", false),
                bool(environment, "OPENHANDS_CONTAINER_ENABLED", false),
                value(environment, "OPENHANDS_CONTAINER_IMAGE", "bug-fixer-openhands:0.1.1"),
                value(environment, "OPENHANDS_PYTHON_BINARY", "python3"),
                appRoot.resolve(value(environment, "OPENHANDS_WORKER_SCRIPT", "runtime/openhands_worker.py")).normalize(),
                OpenHandsProvider.parse(value(environment, "OPENHANDS_PROVIDER", "GEMINI")),
                value(environment, "OPENHANDS_MODEL", "gemini-3.5-flash"),
                firstNonBlank(optional(environment, "OPENHANDS_GEMINI_API_KEY"), optional(environment, "ADK_API_KEY")),
                value(environment, "GROQ_BASE_URL", "https://api.groq.com/openai/v1"),
                optional(environment, "GROQ_API_KEY"),
                optional(environment, "GEMINI_CONNECTOR_BASE_URL"),
                optional(environment, "GEMINI_VERTEX_PROJECT"),
                optional(environment, "GEMINI_VERTEX_LOCATION"),
                integer(environment, "GEMINI_PROXY_PORT", 8787, 1024, 65535),
                integer(environment, "MAX_FIX_ATTEMPTS", 3, 1, 5),
                bool(environment, "ADK_ENABLED", false),
                value(environment, "ADK_MODEL", "gemini-2.5-flash"),
                optional(environment, "ADK_API_KEY"),
                optional(environment, "ADK_BASE_URL"),
                optional(environment, "ADK_AUTHORIZATION"),
                optional(environment, "ADK_CLIENT_ID"),
                optional(environment, "ADK_CLIENT_SECRET"),
                ValidationProfile.parse(value(environment, "VALIDATION_PROFILE", "NONE")),
                value(environment, "NPM_BINARY", "npm"),
                Duration.ofMinutes(integer(environment, "OPENHANDS_TIMEOUT_MINUTES", 15, 1, 60)),
                Duration.ofMinutes(integer(environment, "VALIDATION_TIMEOUT_MINUTES", 20, 1, 90)),
                appRoot.resolve("runtime/work").normalize(),
                bool(environment, "LOCAL_SIMULATION_ENABLED", false),
                bool(environment, "PUBLISHING_ENABLED", false),
                optional(environment, "GITHUB_TOKEN"),
                value(environment, "GITHUB_REPOSITORY", ""),
                optional(environment, "BITBUCKET_BASE_URL"),
                optional(environment, "BITBUCKET_PROJECT_KEY"),
                optional(environment, "BITBUCKET_TOKEN"),
                appRoot.resolve(value(environment, "INVESTIGATION_KNOWLEDGE_FILE", "runtime/investigation-knowledge.md")).normalize());
    }

    private static String required(Environment environment, String name) {
        String value = value(environment, name, null);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be set. Refusing to accept unsigned Jira webhooks.");
        }
        return value;
    }

    private static int integer(Environment environment, String name, int fallback, int min, int max) {
        int value;
        try {
            value = Integer.parseInt(value(environment, name, Integer.toString(fallback)));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException(name + " must be an integer", exception);
        }
        if (value < min || value > max) {
            throw new IllegalStateException(name + " must be between " + min + " and " + max);
        }
        return value;
    }

    private static boolean bool(Environment environment, String name, boolean fallback) {
        return Boolean.parseBoolean(value(environment, name, Boolean.toString(fallback)));
    }

    private static String optional(Environment environment, String name) {
        String value = value(environment, name, null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String preferred, String fallback) {
        return preferred == null || preferred.isBlank() ? fallback : preferred;
    }

    private static Path path(Environment environment, String name) {
        String value = value(environment, name, null);
        return value == null || value.isBlank() ? null : Path.of(value).toAbsolutePath().normalize();
    }

    /**
     * Supports existing deployment variables such as JIRA_WEBHOOK_SECRET and
     * Spring properties such as bugfixer.jira-webhook-secret. Environment
     * variables win, which keeps Secret Manager/Kubernetes deployment values
     * higher priority than application.yaml defaults.
     */
    private static String value(Environment environment, String name, String fallback) {
        String environmentValue = environment.getProperty(name);
        if (environmentValue != null) {
            return environmentValue;
        }
        String propertyName = "bugfixer." + name.toLowerCase(Locale.ROOT).replace('_', '-');
        return environment.getProperty(propertyName, fallback);
    }
}
