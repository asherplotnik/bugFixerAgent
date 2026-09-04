package com.asher.bugfixer.validation;

import com.asher.bugfixer.AppConfig;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Runs only centrally selected command profiles. This is process-safe, but Maven and Gradle
 * can execute repository code, so production must run this class in a credential-free sandbox.
 */
public final class FixedBuildValidator {
    private static final int MAX_OUTPUT_BYTES = 128 * 1024;
    private final AppConfig config;

    public FixedBuildValidator(AppConfig config) {
        this.config = config;
    }

    public ValidationResult validate(Path workspace) throws Exception {
        ValidationProfile profile = config.validationProfile();
        if (profile == ValidationProfile.NONE) {
            return new ValidationResult(ValidationResult.Status.NOT_CONFIGURED, -1, "Validation is not configured.");
        }
        List<List<String>> commands = commandsForCurrentPlatform(profile, workspace, config.npmBinary());
        StringBuilder combinedOutput = new StringBuilder();
        for (List<String> command : commands) {
            ValidationResult result = run(command, workspace);
            combinedOutput.append("$ ").append(String.join(" ", command)).append("\n").append(result.output()).append("\n");
            if (result.status() != ValidationResult.Status.PASSED) {
                return new ValidationResult(result.status(), result.exitCode(), combinedOutput.toString());
            }
        }
        return new ValidationResult(ValidationResult.Status.PASSED, 0, combinedOutput.toString());
    }

    private ValidationResult run(List<String> command, Path workspace) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(workspace.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.remove("JIRA_API_TOKEN");
        environment.remove("JIRA_USER_EMAIL");
        environment.remove("GITHUB_TOKEN");
        environment.remove("GITLAB_TOKEN");
        environment.remove("GEMINI_API_KEY");
        environment.remove("GOOGLE_API_KEY");
        environment.put("CI", "true");

        Process process = builder.start();
        try (ExecutorService readers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            InputStream processOutput = process.getInputStream();
            Future<String> output = readers.submit(() -> readLimited(processOutput));
            boolean completed = process.waitFor(config.validationTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                terminateProcessTree(process);
                processOutput.close();
                return new ValidationResult(ValidationResult.Status.TIMED_OUT, -1, awaitOutput(output) + "\nValidation timed out.");
            }
            return new ValidationResult(
                    process.exitValue() == 0 ? ValidationResult.Status.PASSED : ValidationResult.Status.FAILED,
                    process.exitValue(),
                    awaitOutput(output));
        }
    }

    static List<List<String>> commandsForCurrentPlatform(ValidationProfile profile, Path workspace, String npmBinary) {
        if (profile == ValidationProfile.NONE) {
            return List.of();
        }
        boolean windows = System.getProperty("os.name").toLowerCase().contains("win");
        if (profile == ValidationProfile.NPM_TEST) {
            return List.of(List.of(npmBinary, "test"));
        }
        if (profile == ValidationProfile.NPM_CI_TEST) {
            return List.of(
                    List.of(npmBinary, "ci", "--ignore-scripts", "--no-audit", "--no-fund"),
                    List.of(npmBinary, "test"));
        }
        String wrapper = profile == ValidationProfile.MAVEN_VERIFY ? "mvnw" : "gradlew";
        if (windows && Files.exists(workspace.resolve(wrapper + ".cmd"))) {
            return List.of(profile == ValidationProfile.MAVEN_VERIFY
                    ? List.of(wrapper + ".cmd", "--batch-mode", "--no-transfer-progress", "verify")
                    : List.of(wrapper + ".bat", "--no-daemon", "check"));
        }
        if (Files.exists(workspace.resolve(wrapper))) {
            return List.of(profile.command());
        }
        return List.of(profile == ValidationProfile.MAVEN_VERIFY
                ? List.of("mvn", "--batch-mode", "--no-transfer-progress", "verify")
                : List.of("gradle", "--no-daemon", "check"));
    }

    private String readLimited(InputStream stream) throws IOException {
        try (stream;
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Validation output: " + line);
                byte[] bytes = (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8);
                int remaining = MAX_OUTPUT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(bytes, 0, Math.min(bytes.length, remaining));
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String awaitOutput(Future<String> output) throws InterruptedException, ExecutionException {
        return output.get();
    }

    private void terminateProcessTree(Process process) {
        process.descendants().forEach(descendant -> descendant.destroyForcibly());
        process.destroyForcibly();
    }
}
