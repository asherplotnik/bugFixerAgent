package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Creates exactly one Bitbucket pull request after a validated fix is committed and pushed. */
final class BitbucketPullRequestPublisher implements PullRequestPublisher {
    private final AppConfig config;
    private final BitbucketRepositoryTool bitbucket;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    BitbucketPullRequestPublisher(AppConfig config) {
        this.config = config;
        this.bitbucket = new BitbucketRepositoryTool(config);
    }

    @Override
    public Publication publish(JiraIssue issue, Path workspace) throws Exception {
        GitCommandRunner.Result diff = GitCommandRunner.run(workspace, List.of("status", "--porcelain"));
        if (diff.exitCode() != 0 || diff.output().isBlank()) {
            throw new IllegalStateException("Refusing to create a Bitbucket PR without a validated source change.");
        }
        bitbucket.commitAndPush(issue, workspace);
        String branch = bitbucket.branchName(issue.key());
        Map<String, Object> repository = Map.of(
                "slug", bitbucket.repositoryName(),
                "project", Map.of("key", bitbucket.projectKey()));
        Map<String, Object> payload = Map.of(
                "title", "[" + issue.key() + "] Automated bug fix",
                "description", "Automated fix. Trusted validation completed before this pull request was created.",
                "fromRef", Map.of("id", "refs/heads/" + branch, "repository", repository),
                "toRef", Map.of("id", "refs/heads/" + config.targetBranch(), "repository", repository));
        HttpRequest request = HttpRequest.newBuilder(URI.create(bitbucket.pullRequestsUrl()))
                .header("Authorization", bitbucket.apiHeaders().get("Authorization"))
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 201) {
            throw new IllegalStateException("Bitbucket pull request creation failed (HTTP " + response.statusCode() + "): " + response.body());
        }
        JsonNode json = mapper.readTree(response.body());
        JsonNode self = json.path("links").path("self");
        String url = self.isArray() && !self.isEmpty() ? self.get(0).path("href").asText() : "created";
        return new Publication(true, "Created Bitbucket pull request: " + url);
    }
}
