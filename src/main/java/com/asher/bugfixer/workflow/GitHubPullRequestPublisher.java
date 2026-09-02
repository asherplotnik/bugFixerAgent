package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Explicitly enabled publisher. It uses a configured GitHub repository and token;
 * neither the Jira payload nor OpenCode can select the target, branch, or API request.
 */
public final class GitHubPullRequestPublisher implements PullRequestPublisher {
    private final AppConfig config;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    public GitHubPullRequestPublisher(AppConfig config) {
        if (config.githubToken() == null || config.githubToken().isBlank()) {
            throw new IllegalStateException("PUBLISHING_ENABLED requires GITHUB_TOKEN.");
        }
        if (!config.githubRepository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) {
            throw new IllegalStateException("GITHUB_REPOSITORY must be an approved owner/repository value.");
        }
        this.config = config;
    }

    @Override
    public Publication publish(JiraIssue issue, Path workspace) throws Exception {
        GitCommandRunner.Result diff = GitCommandRunner.run(workspace, List.of("status", "--porcelain"));
        if (diff.exitCode() != 0) {
            throw new IllegalStateException("Unable to inspect workspace changes: " + diff.output());
        }
        if (diff.output().isBlank()) {
            throw new IllegalStateException("OpenCode and validation passed but no source changes exist; refusing to create an empty PR.");
        }

        String branch = branchName(issue.key());
        GitCommandRunner.requireSuccess(workspace, List.of("checkout", "-b", branch), "creating the fix branch");
        GitCommandRunner.requireSuccess(workspace, List.of("config", "user.name", "Bug Fixer Agent"), "configuring the commit author");
        GitCommandRunner.requireSuccess(workspace, List.of("config", "user.email", "bug-fixer-agent@users.noreply.github.com"), "configuring the commit author");
        GitCommandRunner.requireSuccess(workspace, List.of("add", "--all"), "staging the validated change");
        GitCommandRunner.requireSuccess(workspace,
                List.of("commit", "-m", "fix(" + safeIssueKey(issue.key()) + "): automated repair"),
                "committing the validated change");
        GitCommandRunner.requireSuccess(workspace,
                List.of("remote", "set-url", "origin", "https://github.com/" + config.githubRepository() + ".git"),
                "selecting the approved GitHub remote");
        GitCommandRunner.requireSuccess(workspace, List.of("push", "--set-upstream", "origin", branch), "pushing the fix branch");

        Map<String, Object> payload = Map.of(
                "title", "[" + safeIssueKey(issue.key()) + "] Automated bug fix",
                "head", branch,
                "base", config.targetBranch(),
                "body", "Automated local simulation for " + safeIssueKey(issue.key()) + ". Validation completed before this draft PR was created.",
                "draft", true);
        HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.github.com/repos/" + config.githubRepository() + "/pulls"))
                .header("Accept", "application/vnd.github+json")
                .header("Authorization", "Bearer " + config.githubToken())
                .header("X-GitHub-Api-Version", "2022-11-28")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IllegalStateException("GitHub pull request creation failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        return new Publication(true, "Created draft pull request: " + json.path("html_url").asText());
    }

    private String branchName(String issueKey) {
        return "bugfix/" + safeIssueKey(issueKey).toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private String safeIssueKey(String issueKey) {
        return issueKey.replaceAll("[^A-Za-z0-9_-]", "-");
    }
}
