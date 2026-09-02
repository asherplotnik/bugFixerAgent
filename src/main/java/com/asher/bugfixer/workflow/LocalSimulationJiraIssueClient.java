package com.asher.bugfixer.workflow;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.domain.JiraIssue;

/**
 * Local-only test adapter. It is enabled only by LOCAL_SIMULATION_ENABLED and
 * makes the signed webhook summary available as the simulated Jira issue.
 * Production must use HttpJiraIssueClient, which re-fetches authoritative Jira data.
 */
public final class LocalSimulationJiraIssueClient implements JiraIssueClient {
    private final String agentReadyStatus;

    public LocalSimulationJiraIssueClient(AppConfig config) {
        this.agentReadyStatus = config.agentReadyStatus();
    }

    @Override
    public JiraIssue fetch(BugFixRequest request) {
        return new JiraIssue(
                request.issueId(),
                request.issueKey(),
                request.summary(),
                "This is a local simulation. Treat the signed webhook summary as the bug report.",
                agentReadyStatus);
    }
}
