package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.adk.AdkFixCoordinator;
import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.domain.WorkflowResult;
import com.asher.bugfixer.opencode.FixResult;
import com.asher.bugfixer.opencode.OpenCodeCliFixer;
import com.asher.bugfixer.opencode.OpenCodeFixer;
import com.asher.bugfixer.validation.FixedBuildValidator;
import com.asher.bugfixer.validation.ValidationResult;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Controlled local workflow: notification -> authoritative Jira read -> isolated workspace -> fix -> validation. */
public final class BugFixWorkflow {
    private final AppConfig config;
    private final JiraIssueClient jira;
    private final WorkspaceManager workspaces;
    private final OpenCodeFixer fixer;
    private final FixedBuildValidator validator;
    private final DryRunPublisher publisher;

    private BugFixWorkflow(
            AppConfig config,
            JiraIssueClient jira,
            WorkspaceManager workspaces,
            OpenCodeFixer fixer,
            FixedBuildValidator validator,
            DryRunPublisher publisher) {
        this.config = config;
        this.jira = jira;
        this.workspaces = workspaces;
        this.fixer = fixer;
        this.validator = validator;
        this.publisher = publisher;
    }

    public static BugFixWorkflow create(AppConfig config) {
        JiraIssueClient jira = config.jiraBaseUrl() == null || config.jiraUserEmail() == null || config.jiraApiToken() == null
                ? new UnavailableJiraIssueClient()
                : new HttpJiraIssueClient(config.jiraBaseUrl(), config.jiraUserEmail(), config.jiraApiToken());
        OpenCodeFixer fixer = new OpenCodeCliFixer(config);
        if (config.adkEnabled()) {
            fixer = new AdkFixCoordinator(fixer, config.adkModel());
        }
        return new BugFixWorkflow(
                config,
                jira,
                new WorkspaceManager(config),
                fixer,
                new FixedBuildValidator(config),
                new DryRunPublisher());
    }

    public WorkflowResult execute(BugFixRequest request) throws Exception {
        List<String> notes = new ArrayList<>();
        JiraIssue issue = jira.fetch(request.issueKey());
        if (!config.agentReadyStatus().equals(issue.status())) {
            return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                    "Issue is no longer in the configured agent-ready status.", notes);
        }
        if (!config.opencodeEnabled()) {
            return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                    "OPENCODE_ENABLED is false; no source files were modified.", notes);
        }

        Path workspace = workspaces.prepare(request);
        String feedback = "No validation has run yet.";
        for (int attempt = 1; attempt <= config.maxFixAttempts(); attempt++) {
            FixResult fix = fixer.fix(issue, config.targetRepositoryName(), workspace, feedback);
            notes.add("OpenCode attempt " + attempt + " exited with " + fix.exitCode());
            if (!fix.succeeded()) {
                feedback = "OpenCode did not complete successfully:\n" + fix.output();
                continue;
            }

            ValidationResult validation = validator.validate(workspace);
            notes.add("Validation result: " + validation.status());
            if (validation.passed()) {
                notes.add(publisher.publish(issue, workspace));
                return new WorkflowResult(WorkflowResult.Status.COMPLETED_DRY_RUN,
                        "Fix and configured validation completed. Publishing remains disabled.", notes);
            }
            if (validation.status() == ValidationResult.Status.NOT_CONFIGURED) {
                return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                        "Fix completed but no validation profile is configured; no publish attempted.", notes);
            }
            feedback = "The trusted validation command failed (exit " + validation.exitCode() + "):\n" + validation.output();
        }
        return new WorkflowResult(WorkflowResult.Status.FAILED,
                "OpenCode did not produce a passing change within the configured attempt limit.", notes);
    }
}
