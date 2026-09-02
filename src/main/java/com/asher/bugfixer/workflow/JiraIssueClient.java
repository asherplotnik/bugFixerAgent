package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.BugFixRequest;
import com.asher.bugfixer.domain.JiraIssue;

public interface JiraIssueClient {
    JiraIssue fetch(BugFixRequest request) throws Exception;
}
