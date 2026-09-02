package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.JiraIssue;
import java.nio.file.Path;

/** Placeholder for the trusted publisher. This MVP deliberately never pushes or creates a PR. */
public final class DryRunPublisher implements PullRequestPublisher {
    @Override
    public Publication publish(JiraIssue issue, Path workspace) {
        return new Publication(false, "Dry run complete for " + issue.key() + ". Workspace retained at " + workspace
                + "; no branch, commit, pull request, Jira comment, or transition was created.");
    }
}
