package com.asher.bugfixer.openhands;

import com.asher.bugfixer.domain.JiraIssue;
import java.nio.file.Path;

/** Executes a policy-bound OpenHands worker in a prepared workspace. */
public interface OpenHandsFixer {
    FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception;
}
