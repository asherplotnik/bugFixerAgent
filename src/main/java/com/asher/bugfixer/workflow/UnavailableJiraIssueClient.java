package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.domain.JiraIssue;

/** Safe default when Jira API credentials have not been configured. */
public final class UnavailableJiraIssueClient implements JiraIssueClient {
    @Override
    public JiraIssue fetch(BugFixRequest request) {
        throw new IllegalStateException("Jira API is not configured. A webhook notification alone cannot start a code change.");
    }
}
