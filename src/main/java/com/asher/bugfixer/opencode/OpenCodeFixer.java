package com.asher.bugfixer.opencode;

import com.asher.bugfixer.domain.JiraIssue;
import java.nio.file.Path;

public interface OpenCodeFixer {
    FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception;
}
