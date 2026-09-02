package com.asher.bugfixer.adk;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.openhands.FixResult;
import com.asher.bugfixer.openhands.OpenHandsFixer;
import java.nio.file.Path;

/**
 * Executes the ADK-defined OpenHands tool with trusted, workflow-bound arguments.
 *
 * <p>There is deliberately no second LLM deciding whether to call the only allowed tool:
 * OpenHands is the code-changing model, while ADK provides the typed policy boundary.</p>
 */
public final class AdkFixCoordinator implements OpenHandsFixer {
    private final OpenHandsFixer delegate;

    public AdkFixCoordinator(OpenHandsFixer delegate, AppConfig config) {
        this.delegate = delegate;
    }

    @Override
    public FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception {
        try {
            AdkOpenHandsTool.bind(delegate, issue, repositoryName, workspace);
            AdkOpenHandsTool.runOpenHandsFix(validationFeedback);
            FixResult result = AdkOpenHandsTool.unbind();
            return result == null
                    ? new FixResult(true, false, -1, "The authorized OpenHands tool did not return a result.")
                    : result;
        } finally {
            AdkOpenHandsTool.unbind();
        }
    }
}
