package com.asher.bugfixer;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the Spring Boot Jira webhook receiver. */
@SpringBootApplication
public final class BugFixerApplication {
    private BugFixerApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(BugFixerApplication.class, args);
    }
}
