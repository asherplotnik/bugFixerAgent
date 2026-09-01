package com.asher.bugfixer.adk;

import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.opencode.FixResult;
import com.asher.bugfixer.opencode.OpenCodeFixer;
import com.google.adk.tools.Annotations.Schema;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * ADK function-tool facade for OpenCode. It never accepts a path, command, repository, or branch
 * from the model; those are bound by the deterministic workflow immediately before invocation.
 */
public final class AdkOpenCodeTool {
    private static final AtomicReference<Invocation> INVOCATION = new AtomicReference<>();

    private AdkOpenCodeTool() {
    }

    public static void bind(OpenCodeFixer fixer, JiraIssue issue, String repositoryName, Path workspace) {
        if (!INVOCATION.compareAndSet(null, new Invocation(fixer, issue, repositoryName, workspace))) {
            throw new IllegalStateException("Only one local ADK OpenCode invocation may run at a time.");
        }
    }

    public static FixResult unbind() {
        Invocation invocation = INVOCATION.getAndSet(null);
        return invocation == null ? null : invocation.result.get();
    }

    @Schema(description = "Run the pre-authorized OpenCode fix operation in the prepared workspace.")
    public static Map<String, Object> runOpenCodeFix(
            @Schema(name = "validationFeedback", description = "Compiler or test failures from the trusted validator. Empty for the first attempt.")
            String validationFeedback) {
        Invocation invocation = INVOCATION.get();
        if (invocation == null) {
            return Map.of("status", "error", "message", "No authorized workflow invocation is active.");
        }
        try {
            FixResult result = invocation.fixer.fix(
                    invocation.issue,
                    invocation.repositoryName,
                    invocation.workspace,
                    validationFeedback == null ? "" : validationFeedback);
            invocation.result.set(result);
            return Map.of("status", result.succeeded() ? "success" : "error", "exitCode", result.exitCode(), "output", result.output());
        } catch (Exception exception) {
            return Map.of("status", "error", "message", "OpenCode invocation failed: " + exception.getMessage());
        }
    }

    private static final class Invocation {
        private final OpenCodeFixer fixer;
        private final JiraIssue issue;
        private final String repositoryName;
        private final Path workspace;
        private final AtomicReference<FixResult> result = new AtomicReference<>();

        private Invocation(OpenCodeFixer fixer, JiraIssue issue, String repositoryName, Path workspace) {
            this.fixer = fixer;
            this.issue = issue;
            this.repositoryName = repositoryName;
            this.workspace = workspace;
        }
    }
}
