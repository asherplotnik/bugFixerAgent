package com.asher.bugfixer.domain;

/** Current Jira state retrieved by the worker after receiving a webhook notification. */
public record JiraIssue(String id, String key, String summary, String description, String status) {
}
