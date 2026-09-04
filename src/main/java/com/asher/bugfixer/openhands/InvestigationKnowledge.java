package com.asher.bugfixer.openhands;

import com.asher.bugfixer.AppConfig;
import java.nio.file.Files;

/** Loads deployment-owned investigation context for the model prompt. */
final class InvestigationKnowledge {
    private static final int MAX_CHARS = 8_000;

    private InvestigationKnowledge() {
    }

    static String load(AppConfig config) {
        try {
            if (!Files.isRegularFile(config.investigationKnowledgeFile())) {
                return "No additional repository knowledge is configured.";
            }
            String text = Files.readString(config.investigationKnowledgeFile()).trim();
            if (text.isBlank()) {
                return "No additional repository knowledge is configured.";
            }
            return text.length() <= MAX_CHARS ? text : text.substring(0, MAX_CHARS) + "\n[truncated]";
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to load INVESTIGATION_KNOWLEDGE_FILE.", exception);
        }
    }
}
