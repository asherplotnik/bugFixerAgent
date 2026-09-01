package com.asher.bugfixer;

import com.asher.bugfixer.workflow.BugFixWorkflow;
import com.asher.bugfixer.workflow.InMemoryRequestQueue;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/** Starts the local-only worker only when explicitly configured. */
public final class WorkflowWorker {
    private final AppConfig config;
    private final InMemoryRequestQueue queue;
    private final BugFixWorkflow workflow;

    WorkflowWorker(AppConfig config, InMemoryRequestQueue queue, BugFixWorkflow workflow) {
        this.config = config;
        this.queue = queue;
        this.workflow = workflow;
    }

    @PostConstruct
    void start() {
        if (config.workerEnabled()) {
            queue.startWorker(workflow);
        }
    }

    @PreDestroy
    void stop() {
        queue.stop();
    }
}
