package com.asher.bugfixer;

import com.asher.bugfixer.domain.OneShotWorkerRequest;
import com.asher.bugfixer.domain.WorkflowResult;
import com.asher.bugfixer.workflow.BugFixWorkflow;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;

/** Starts either the Jira webhook receiver or one Kubernetes Job worker invocation. */
@SpringBootApplication
public final class BugFixerApplication {
    private BugFixerApplication() {
    }

    public static void main(String[] args) {
        boolean oneShotWorker = Boolean.parseBoolean(System.getenv("WORKER_MODE"));
        SpringApplication application = new SpringApplication(BugFixerApplication.class);
        if (!oneShotWorker) {
            application.run(args);
            return;
        }

        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(args)) {
            BugFixWorkflow workflow = context.getBean(BugFixWorkflow.class);
            WorkflowResult result = workflow.execute(OneShotWorkerRequest.from(System.getenv()));
            System.out.println("One-shot worker completed: status=" + result.status() + " message=" + result.message());
            if (result.status() == WorkflowResult.Status.FAILED) {
                System.exit(1);
            }
        } catch (Exception exception) {
            System.err.println("One-shot worker failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            System.exit(1);
        }
    }
}
