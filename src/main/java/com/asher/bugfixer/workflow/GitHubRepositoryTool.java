package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

/** Trusted clone path for the single configured GitHub repository. */
final class GitHubRepositoryTool {
    private final AppConfig config;
    private final Map<String, String> gitEnvironment;

    GitHubRepositoryTool(AppConfig config) {
        if (!config.githubRepository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalStateException("GITHUB_REPOSITORY must be an approved owner/repository value.");
        }
        if (config.githubToken() == null || config.githubToken().isBlank()) {
            throw new IllegalStateException("GITHUB_TOKEN must be configured to clone the approved GitHub repository.");
        }
        this.config = config;
        this.gitEnvironment = Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "http.extraHeader",
                "GIT_CONFIG_VALUE_0", gitAuthorizationHeader(config.githubToken()),
                "GIT_TERMINAL_PROMPT", "0");
    }

    void cloneRepository(Path directory, Path workspace) throws Exception {
        GitCommandRunner.requireSuccess(directory,
                List.of("clone", "--branch", config.targetBranch(), "--single-branch", cloneUrl(), workspace.toString()),
                gitEnvironment,
                "cloning the approved GitHub repository");
    }

    Map<String, String> gitEnvironment() {
        return gitEnvironment;
    }

    private String cloneUrl() {
        return "https://github.com/" + config.githubRepository() + ".git";
    }

    private static String gitAuthorizationHeader(String token) {
        String credentials = "x-access-token:" + token;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        return "Authorization: Basic " + encoded;
    }
}
