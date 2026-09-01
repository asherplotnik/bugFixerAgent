package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.JiraIssue;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/** Jira Cloud reader. It is deliberately read-only; transitions and comments are not implemented yet. */
public final class HttpJiraIssueClient implements JiraIssueClient {
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final ObjectMapper mapper = new ObjectMapper();
    private final URI baseUri;
    private final String authorization;

    public HttpJiraIssueClient(String baseUrl, String email, String apiToken) {
        this.baseUri = URI.create(baseUrl.endsWith("/") ? baseUrl : baseUrl + "/");
        String token = email + ":" + apiToken;
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(token.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public JiraIssue fetch(String issueKey) throws Exception {
        String key = URLEncoder.encode(issueKey, StandardCharsets.UTF_8);
        URI uri = baseUri.resolve("rest/api/3/issue/" + key + "?fields=summary,description,status");
        HttpRequest request = HttpRequest.newBuilder(uri)
                .header("Accept", "application/json")
                .header("Authorization", authorization)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Jira issue fetch returned HTTP " + response.statusCode());
        }
        JsonNode root = mapper.readTree(response.body());
        JsonNode fields = root.path("fields");
        return new JiraIssue(
                root.path("id").asText(),
                root.path("key").asText(),
                fields.path("summary").asText(),
                mapper.writeValueAsString(fields.path("description")),
                fields.path("status").path("name").asText());
    }
}
