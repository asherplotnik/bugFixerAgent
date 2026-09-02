package com.asher.bugfixer.workflow;

import com.asher.bugfixer.domain.JiraIssue;
import java.nio.file.Path;

interface PullRequestPublisher {
    Publication publish(JiraIssue issue, Path workspace) throws Exception;

    record Publication(boolean published, String message) {
    }
}
