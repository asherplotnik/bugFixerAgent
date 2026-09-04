package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Trusted, read-only Bitbucket Data Center investigation operations.
 *
 * <p>All project, repository, branch, and file-path values are validated before a request or Git process is made.
 * This class intentionally has no branch, commit, push, pull-request, approval, or merge operation.</p>
 */
public final class BitbucketReadOnlyInvestigationTool {
    private static final Set<String> ALLOWED_PROJECTS = Set.of("DMS", "DIRM");
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_FILE_BYTES = 128 * 1024;
    private final String baseUrl;
    private final String authorization;
    private final Map<String, String> gitEnvironment;
    private final HttpClient http = HttpClient.newHttpClient();
    private final JsonMapper mapper = JsonMapper.builder().build();

    public BitbucketReadOnlyInvestigationTool(AppConfig config) {
        this.baseUrl = trustedBaseUrl(config.bitbucketBaseUrl());
        String token = required(config.bitbucketToken(), "BITBUCKET_TOKEN");
        this.authorization = "Bearer " + token;
        this.gitEnvironment = Map.of(
                "GIT_CONFIG_COUNT", "1",
                "GIT_CONFIG_KEY_0", "http.extraHeader",
                "GIT_CONFIG_VALUE_0", "Authorization: " + authorization,
                "GIT_TERMINAL_PROMPT", "0");
    }

    /** Finds repository candidates by name in the approved service and shared-module projects. */
    public List<Repository> findRepositories(String nameFragment) throws Exception {
        String query = requiredNameFragment(nameFragment).toLowerCase(Locale.ROOT);
        List<Repository> matches = new ArrayList<>();
        for (String project : ALLOWED_PROJECTS) {
            int start = 0;
            while (true) {
                JsonNode page = getJson("/projects/" + project + "/repos?limit=" + MAX_PAGE_SIZE + "&start=" + start);
                for (JsonNode value : page.path("values")) {
                    String slug = value.path("slug").asText();
                    String name = value.path("name").asText();
                    if (slug.toLowerCase(Locale.ROOT).contains(query) || name.toLowerCase(Locale.ROOT).contains(query)) {
                        matches.add(new Repository(project, slug, name));
                    }
                }
                if (page.path("isLastPage").asBoolean(true)) {
                    break;
                }
                start = page.path("nextPageStart").asInt(-1);
                if (start < 0) {
                    throw new IllegalStateException("Bitbucket returned an invalid repository pagination response.");
                }
            }
        }
        return List.copyOf(matches);
    }

    /** Lists files at a safe relative path and branch without reading file content. */
    public List<String> listFiles(String projectKey, String repositoryName, String branch, String path) throws Exception {
        String project = approvedProject(projectKey);
        String repository = safeRepository(repositoryName);
        String ref = safeBranch(branch);
        String normalizedPath = safePath(path, true);
        String query = "?at=" + encode("refs/heads/" + ref)
                + (normalizedPath.isEmpty() ? "" : "&path=" + encode(normalizedPath));
        JsonNode response = getJson("/projects/" + project + "/repos/" + repository + "/files" + query);
        List<String> files = new ArrayList<>();
        for (JsonNode value : response.path("values")) {
            files.add(value.asText());
        }
        return List.copyOf(files);
    }

    /** Reads one bounded source file from an approved repository. */
    public String readFile(String projectKey, String repositoryName, String branch, String path) throws Exception {
        String project = approvedProject(projectKey);
        String repository = safeRepository(repositoryName);
        String ref = safeBranch(branch);
        String normalizedPath = safePath(path, false);
        HttpRequest request = request("/projects/" + project + "/repos/" + repository + "/raw/" + normalizedPath
                + "?at=" + encode("refs/heads/" + ref));
        HttpResponse<byte[]> response = http.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Bitbucket file read failed (HTTP " + response.statusCode() + ").");
        }
        if (response.body().length > MAX_FILE_BYTES) {
            throw new IllegalStateException("Bitbucket file exceeds the " + MAX_FILE_BYTES + " byte investigation limit.");
        }
        return new String(response.body(), StandardCharsets.UTF_8);
    }

    /** Clones a selected candidate without credentials in its remote URL, then marks the workspace read-only. */
    public Path cloneReadOnly(String projectKey, String repositoryName, String branch, Path investigationRoot) throws Exception {
        String project = approvedProject(projectKey);
        String repository = safeRepository(repositoryName);
        String ref = safeBranch(branch);
        Files.createDirectories(investigationRoot);
        Path workspace = Files.createTempDirectory(investigationRoot, project.toLowerCase(Locale.ROOT) + "-" + repository + "-");
        GitCommandRunner.requireSuccess(investigationRoot,
                List.of("clone", "--depth", "1", "--branch", ref, cloneUrl(project, repository), workspace.toString()),
                gitEnvironment,
                "cloning a read-only Bitbucket investigation workspace");
        makeReadOnly(workspace);
        return workspace;
    }

    private JsonNode getJson(String pathAndQuery) throws Exception {
        HttpResponse<String> response = http.send(request(pathAndQuery), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Bitbucket read-only request failed (HTTP " + response.statusCode() + ").");
        }
        return mapper.readTree(response.body());
    }

    private HttpRequest request(String pathAndQuery) {
        return HttpRequest.newBuilder(URI.create(baseUrl + "/rest/api/1.0" + pathAndQuery))
                .header("Authorization", authorization)
                .GET()
                .build();
    }

    private String cloneUrl(String projectKey, String repositoryName) {
        return baseUrl + "/scm/" + projectKey + "/" + repositoryName + ".git";
    }

    private static void makeReadOnly(Path root) throws IOException {
        try (var files = Files.walk(root)) {
            for (Path path : files.toList()) {
                path.toFile().setWritable(false, false);
            }
        }
    }

    private static String trustedBaseUrl(String value) {
        String url = required(value, "BITBUCKET_BASE_URL").replaceAll("/+$", "");
        URI uri = URI.create(url);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalStateException("BITBUCKET_BASE_URL must be an HTTPS server URL without credentials, query, or fragment.");
        }
        return url;
    }

    private static String approvedProject(String value) {
        String project = required(value, "project key").toUpperCase(Locale.ROOT);
        if (!ALLOWED_PROJECTS.contains(project)) {
            throw new IllegalArgumentException("Bitbucket project must be one of " + ALLOWED_PROJECTS + ".");
        }
        return project;
    }

    private static String safeRepository(String value) {
        String repository = required(value, "repository name");
        if (!repository.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("Bitbucket repository name is invalid.");
        }
        return repository;
    }

    private static String safeBranch(String value) {
        String branch = required(value, "branch");
        if (!branch.matches("[A-Za-z0-9._/-]+") || branch.startsWith("/") || branch.contains("..")) {
            throw new IllegalArgumentException("Bitbucket branch is invalid.");
        }
        return branch;
    }

    private static String safePath(String value, boolean allowEmpty) {
        String path = value == null ? "" : value.trim();
        if (path.isEmpty() && allowEmpty) {
            return path;
        }
        if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.contains("..") || !path.matches("[A-Za-z0-9._/@+=:-]+")) {
            throw new IllegalArgumentException("Bitbucket file path is invalid.");
        }
        return path;
    }

    private static String requiredNameFragment(String value) {
        String name = required(value, "repository search text");
        if (!name.matches("[A-Za-z0-9._-]{2,100}")) {
            throw new IllegalArgumentException("Repository search text must be 2-100 safe characters.");
        }
        return name;
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured.");
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Repository(String projectKey, String slug, String name) {
    }
}
