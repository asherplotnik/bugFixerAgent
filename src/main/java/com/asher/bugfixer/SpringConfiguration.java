package com.asher.bugfixer;

import com.asher.bugfixer.http.DeliveryDeduplicator;
import com.asher.bugfixer.http.JiraWebhookParser;
import com.asher.bugfixer.http.WebhookSignatureVerifier;
import com.asher.bugfixer.workflow.BugFixWorkflow;
import com.asher.bugfixer.workflow.InMemoryRequestQueue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class SpringConfiguration {
    @Bean
    AppConfig appConfig(Environment environment) {
        return AppConfig.from(environment);
    }

    @Bean
    WebhookSignatureVerifier webhookSignatureVerifier(AppConfig config) {
        return new WebhookSignatureVerifier(config.jiraWebhookSecret());
    }

    @Bean
    JiraWebhookParser jiraWebhookParser() {
        return new JiraWebhookParser();
    }

    @Bean
    DeliveryDeduplicator deliveryDeduplicator() {
        return new DeliveryDeduplicator();
    }

    @Bean
    InMemoryRequestQueue requestQueue() {
        return new InMemoryRequestQueue();
    }

    @Bean
    BugFixWorkflow bugFixWorkflow(AppConfig config) throws Exception {
        return BugFixWorkflow.create(config);
    }

    @Bean
    WorkflowWorker workflowWorker(AppConfig config, InMemoryRequestQueue queue, BugFixWorkflow workflow) {
        return new WorkflowWorker(config, queue, workflow);
    }
}
