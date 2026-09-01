package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.JiraIssue;
import java.nio.file.Path;

/** Placeholder for the trusted publisher. This MVP deliberately never pushes or creates a PR. */
public final class DryRunPublisher {
    public String publish(JiraIssue issue, Path workspace) {
        return "Dry run complete for " + issue.key() + ". Workspace retained at " + workspace
                + "; no branch, commit, pull request, Jira comment, or transition was created.";
    }
}
