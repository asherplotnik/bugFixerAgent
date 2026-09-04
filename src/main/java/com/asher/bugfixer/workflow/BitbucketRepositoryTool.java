package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Trusted Bitbucket Data Center Git operations for the configured repository only. */
final class BitbucketRepositoryTool {
    private static final Set<String> ALLOWED_PROJECTS = Set.of("DMS", "DIRM");
    private final AppConfig config;
    private final String baseUrl;
    private final String projectKey;
    private final String repositoryName;
    private final Map<String, String> gitEnvironment;

    BitbucketRepositoryTool(AppConfig config) {
        this.config = config;
        this.baseUrl = requiredHttpsUrl(config.bitbucketBaseUrl(), "BITBUCKET_BASE_URL");
        this.projectKey = requiredProjectKey(config.bitbucketProjectKey());
        this.repositoryName = requiredRepositoryName(config.targetRepositoryName());
        String token = required(config.bitbucketToken(), "BITBUCKET_TOKEN");
        this.gitEnvironment = Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "http.extraHeader",
                "GIT_CONFIG_VALUE_0", "Authorization: Bearer " + token,
                "GIT_TERMINAL_PROMPT", "0");
    }

    void cloneRepository(Path directory, Path workspace) throws Exception {
        GitCommandRunner.requireSuccess(directory,
                List.of("clone", "--branch", config.targetBranch(), "--single-branch", cloneUrl(), workspace.toString()),
                gitEnvironment,
                "cloning the approved Bitbucket repository");
    }

    void commitAndPush(JiraIssue issue, Path workspace) throws Exception {
        String branch = branchName(issue.key());
        GitCommandRunner.requireSuccess(workspace, List.of("checkout", "-b", branch), "creating the approved fix branch");
        GitCommandRunner.requireSuccess(workspace, List.of("config", "user.name", "Bug Fixer Agent"), "configuring the commit author");
        GitCommandRunner.requireSuccess(workspace, List.of("config", "user.email", "bug-fixer-agent@invalid.local"), "configuring the commit author");
        GitCommandRunner.requireSuccess(workspace, List.of("add", "--all"), "staging the validated change");
        GitCommandRunner.requireSuccess(workspace,
                List.of("commit", "-m", "fix(" + safeIssueKey(issue.key()) + "): automated repair"),
                "committing the validated change");
        // Restore the trusted remote after the agent has edited the disposable workspace.
        GitCommandRunner.requireSuccess(workspace, List.of("remote", "set-url", "origin", cloneUrl()), "selecting the approved Bitbucket remote");
        GitCommandRunner.requireSuccess(workspace,
                List.of("push", "--set-upstream", "origin", branch),
                gitEnvironment,
                "pushing the fix branch");
    }

    String pullRequestsUrl() {
        return baseUrl + "/rest/api/1.0/projects/" + projectKey + "/repos/" + repositoryName + "/pull-requests";
    }

    String projectKey() {
        return projectKey;
    }

    String repositoryName() {
        return repositoryName;
    }

    String branchName(String issueKey) {
        return "bugfix/" + safeIssueKey(issueKey).toLowerCase();
    }

    Map<String, String> apiHeaders() {
        return Map.of("Authorization", "Bearer " + required(config.bitbucketToken(), "BITBUCKET_TOKEN"));
    }

    private String cloneUrl() {
        return baseUrl + "/scm/" + projectKey + "/" + repositoryName + ".git";
    }

    private static String requiredHttpsUrl(String value, String name) {
        String url = required(value, name).replaceAll("/+$", "");
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException(name + " must be an HTTPS server URL without credentials, query, or fragment.");
        }
        return url;
    }

    private static String requiredProjectKey(String value) {
        String key = required(value, "BITBUCKET_PROJECT_KEY").toUpperCase();
        if (!ALLOWED_PROJECTS.contains(key)) {
            throw new IllegalStateException("BITBUCKET_PROJECT_KEY must be one of " + ALLOWED_PROJECTS + ".");
        }
        return key;
    }

    private static String requiredRepositoryName(String value) {
        String name = required(value, "TARGET_REPOSITORY_NAME");
        if (!name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalStateException("TARGET_REPOSITORY_NAME is not a safe Bitbucket repository name.");
        }
        return name;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for Bitbucket operations.");
        }
        return value;
    }

    private static String safeIssueKey(String issueKey) {
        return issueKey.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
