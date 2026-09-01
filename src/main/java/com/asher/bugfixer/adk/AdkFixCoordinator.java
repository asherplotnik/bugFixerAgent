package com.asher.bugfixer.adk;

import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.opencode.FixResult;
import com.asher.bugfixer.opencode.OpenCodeFixer;
import com.google.adk.agents.RunConfig;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import java.nio.file.Path;

/** Runs one ADK invocation and returns only the bound OpenCode result. */
public final class AdkFixCoordinator implements OpenCodeFixer {
    private final OpenCodeFixer delegate;
    private final String model;

    public AdkFixCoordinator(OpenCodeFixer delegate, String model) {
        this.delegate = delegate;
        this.model = model;
    }

    @Override
    public FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception {
        AdkOpenCodeTool.bind(delegate, issue, repositoryName, workspace);
        try {
            InMemoryRunner runner = new InMemoryRunner(AdkBugFixAgent.create(model));
            Session session = runner.sessionService().createSession(runner.appName(), "bug-fix-worker").blockingGet();
            Content request = Content.fromParts(Part.fromText("Call runOpenCodeFix with this validation feedback: " + validationFeedback));
            runner.runAsync(session.userId(), session.id(), request, RunConfig.builder().build()).blockingForEach(event -> { });
            FixResult result = AdkOpenCodeTool.unbind();
            return result == null
                    ? new FixResult(true, false, -1, "ADK completed without calling the authorized OpenCode tool.")
                    : result;
        } finally {
            AdkOpenCodeTool.unbind();
        }
    }
}
