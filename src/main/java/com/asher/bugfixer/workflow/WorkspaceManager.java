package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.BugFixRequest;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Set;

/** Creates an isolated copy; the configured source repository is never edited in place. */
public final class WorkspaceManager {
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", "target", "node_modules", ".gradle");
    private final AppConfig config;

    public WorkspaceManager(AppConfig config) {
        this.config = config;
    }

    public Path prepare(BugFixRequest request) throws IOException {
        Path repository = config.targetRepository();
        if (repository == null || !Files.isDirectory(repository)) {
            throw new IllegalStateException("TARGET_REPOSITORY must point to an approved local repository before a worker can run.");
        }
        Files.createDirectories(config.workspaceRoot());
        Path workspace = Files.createTempDirectory(config.workspaceRoot(), safePrefix(request.issueKey()));
        Files.walkFileTree(repository, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (!directory.equals(repository) && EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                Files.createDirectories(workspace.resolve(repository.relativize(directory).toString()));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (Files.isSymbolicLink(file)) {
                    throw new IOException("Refusing to copy symbolic link from approved repository: " + file);
                }
                Files.copy(file, workspace.resolve(repository.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
                return FileVisitResult.CONTINUE;
            }
        });
        return workspace;
    }

    private String safePrefix(String issueKey) {
        return issueKey.replaceAll("[^a-zA-Z0-9_-]", "_") + "-";
    }
}
