package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.JiraIssue;

public interface JiraIssueClient {
    JiraIssue fetch(String issueKey) throws Exception;
}
