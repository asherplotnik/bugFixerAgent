package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.adk.AdkFixCoordinator;
import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.domain.JiraIssue;
import com.asher.bugfixer.domain.WorkflowResult;
import com.asher.bugfixer.openhands.FixResult;
import com.asher.bugfixer.openhands.DockerOpenHandsFixer;
import com.asher.bugfixer.openhands.KubernetesOpenHandsFixer;
import com.asher.bugfixer.openhands.OpenHandsExecutionMode;
import com.asher.bugfixer.openhands.OpenHandsFixer;
import com.asher.bugfixer.openhands.OpenHandsPythonFixer;
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
    private final OpenHandsFixer fixer;
    private final FixedBuildValidator validator;
    private final PullRequestPublisher publisher;

    private BugFixWorkflow(
            AppConfig config,
            JiraIssueClient jira,
            WorkspaceManager workspaces,
            OpenHandsFixer fixer,
            FixedBuildValidator validator,
            PullRequestPublisher publisher) {
        this.config = config;
        this.jira = jira;
        this.workspaces = workspaces;
        this.fixer = fixer;
        this.validator = validator;
        this.publisher = publisher;
    }

    public static BugFixWorkflow create(AppConfig config) throws Exception {
        JiraIssueClient jira = config.localSimulationEnabled()
                ? new LocalSimulationJiraIssueClient(config)
                : config.jiraBaseUrl() == null || config.jiraUserEmail() == null || config.jiraApiToken() == null
                        ? new UnavailableJiraIssueClient()
                        : new HttpJiraIssueClient(config.jiraBaseUrl(), config.jiraUserEmail(), config.jiraApiToken());
        OpenHandsFixer fixer = switch (config.openhandsExecutionMode()) {
            case LOCAL -> new OpenHandsPythonFixer(config);
            case DOCKER -> new DockerOpenHandsFixer(config);
            case KUBERNETES -> new KubernetesOpenHandsFixer(config);
        };
        if (config.adkEnabled()) {
            fixer = new AdkFixCoordinator(fixer, config);
        }
        PullRequestPublisher publisher = new DryRunPublisher();
        if (config.publishingEnabled()) {
            publisher = config.bitbucketBaseUrl() == null
                    ? new GitHubPullRequestPublisher(config)
                    : new BitbucketPullRequestPublisher(config);
        }
        return new BugFixWorkflow(
                config,
                jira,
                new WorkspaceManager(config),
                fixer,
                new FixedBuildValidator(config),
                publisher);
    }

    public WorkflowResult execute(BugFixRequest request) throws Exception {
        List<String> notes = new ArrayList<>();
        JiraIssue issue = jira.fetch(request);
        log(request.issueKey(), "Jira issue loaded with status=" + issue.status());
        if (!config.agentReadyStatus().equals(issue.status())) {
            return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                    "Issue is no longer in the configured agent-ready status.", notes);
        }
        if (!config.openhandsEnabled()) {
            return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                    "OPENHANDS_ENABLED is false; no source files were modified.", notes);
        }

        log(request.issueKey(), "Preparing isolated repository workspace.");
        Path workspace = workspaces.prepare(request);
        log(request.issueKey(), "Workspace prepared at " + workspace.getFileName());
        String feedback = "No validation has run yet.";
        for (int attempt = 1; attempt <= config.maxFixAttempts(); attempt++) {
            log(request.issueKey(), "Starting OpenHands attempt " + attempt + ".");
            FixResult fix = fixer.fix(issue, config.targetRepositoryName(), workspace, feedback);
            notes.add("OpenHands attempt " + attempt + " exited with " + fix.exitCode());
            log(request.issueKey(), "OpenHands attempt " + attempt + " completed: exit=" + fix.exitCode()
                    + " succeeded=" + fix.succeeded() + " output=" + summarize(fix.output()));
            if (!fix.succeeded()) {
                notes.add("OpenHands failure: " + shorten(fix.output()));
                feedback = "OpenHands did not complete successfully:\n" + fix.output();
                continue;
            }

            log(request.issueKey(), "Starting trusted validation: " + config.validationProfile());
            ValidationResult validation = validator.validate(workspace);
            notes.add("Validation result: " + validation.status());
            log(request.issueKey(), "Validation completed: status=" + validation.status()
                    + " exit=" + validation.exitCode() + " output=" + summarize(validation.output()));
            if (validation.passed()) {
                log(request.issueKey(), "Validation passed; starting pull-request publication.");
                PullRequestPublisher.Publication publication = publisher.publish(issue, workspace);
                notes.add(publication.message());
                log(request.issueKey(), "Pull-request publication completed: published=" + publication.published()
                        + " message=" + publication.message());
                return new WorkflowResult(
                        publication.published() ? WorkflowResult.Status.COMPLETED_PUBLISHED : WorkflowResult.Status.COMPLETED_DRY_RUN,
                        publication.published()
                                ? "Fix, configured validation, and draft pull request creation completed."
                                : "Fix and configured validation completed. Publishing remains disabled.",
                        notes);
            }
            if (validation.status() == ValidationResult.Status.NOT_CONFIGURED) {
                return new WorkflowResult(WorkflowResult.Status.SKIPPED,
                        "Fix completed but no validation profile is configured; no publish attempted.", notes);
            }
            feedback = "The trusted validation command failed (exit " + validation.exitCode() + "):\n" + validation.output();
        }
        return new WorkflowResult(WorkflowResult.Status.FAILED,
                "OpenHands did not produce a passing change within the configured attempt limit.", notes);
    }

    private String shorten(String text) {
        if (text == null || text.isBlank()) {
            return "No output.";
        }
        return text.length() <= 1_000 ? text : text.substring(0, 1_000) + " [truncated]";
    }

    private String summarize(String text) {
        if (text == null || text.isBlank()) {
            return "<no output>";
        }
        String normalized = text.replace('\r', ' ').replace('\n', ' ');
        return normalized.length() <= 4_000 ? normalized : normalized.substring(0, 4_000) + " [truncated]";
    }

    private void log(String issueKey, String message) {
        System.out.println("Bug-fix workflow [" + issueKey + "] " + message);
    }
}
