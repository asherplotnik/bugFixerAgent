package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.BugFixRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Creates a clean Git checkout; the configured source repository is never edited in place. */
public final class WorkspaceManager {
    private final AppConfig config;

    public WorkspaceManager(AppConfig config) {
        this.config = config;
    }

    public Path prepare(BugFixRequest request) throws Exception {
        if (config.bitbucketBaseUrl() != null) {
            Files.createDirectories(config.workspaceRoot());
            Path workspace = Files.createTempDirectory(config.workspaceRoot(), safePrefix(request.issueKey()));
            new BitbucketRepositoryTool(config).cloneRepository(config.workspaceRoot(), workspace);
            return workspace;
        }
        Path repository = config.targetRepository();
        if (repository == null && !config.githubRepository().isBlank()) {
            Files.createDirectories(config.workspaceRoot());
            Path workspace = Files.createTempDirectory(config.workspaceRoot(), safePrefix(request.issueKey()));
            new GitHubRepositoryTool(config).cloneRepository(config.workspaceRoot(), workspace);
            return workspace;
        }
        if (repository == null || !Files.isDirectory(repository.resolve(".git"))) {
            throw new IllegalStateException("TARGET_REPOSITORY must point to an approved local Git repository before a worker can run.");
        }
        String status = GitCommandRunner.run(repository, List.of("status", "--porcelain")).output();
        if (!status.isBlank()) {
            throw new IllegalStateException("TARGET_REPOSITORY has uncommitted changes; refusing to create a fix workspace.");
        }
        Files.createDirectories(config.workspaceRoot());
        Path workspace = Files.createTempDirectory(config.workspaceRoot(), safePrefix(request.issueKey()));
        GitCommandRunner.requireSuccess(
                config.workspaceRoot(),
                List.of("clone", "--branch", config.targetBranch(), "--single-branch", repository.toString(), workspace.toString()),
                "creating an isolated workspace");
        return workspace;
    }

    private String safePrefix(String issueKey) {
        return issueKey.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
    }
}
