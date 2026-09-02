package com.asher.bugfixer.adk;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.opencode.FixResult;
import com.asher.bugfixer.opencode.OpenCodeFixer;
import java.nio.file.Path;

/**
 * Executes the ADK-defined OpenCode tool with trusted, workflow-bound arguments.
 *
 * <p>There is deliberately no second LLM deciding whether to call the only allowed tool:
 * OpenCode is the code-changing model, while ADK provides the typed policy boundary.</p>
 */
public final class AdkFixCoordinator implements OpenCodeFixer {
    private final OpenCodeFixer delegate;

    public AdkFixCoordinator(OpenCodeFixer delegate, AppConfig config) {
        this.delegate = delegate;
    }

    @Override
    public FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception {
        try {
            AdkOpenCodeTool.bind(delegate, issue, repositoryName, workspace);
            AdkOpenCodeTool.runOpenCodeFix(validationFeedback);
            FixResult result = AdkOpenCodeTool.unbind();
            return result == null
                    ? new FixResult(true, false, -1, "The authorized OpenCode tool did not return a result.")
                    : result;
        } finally {
            AdkOpenCodeTool.unbind();
        }
    }
}
