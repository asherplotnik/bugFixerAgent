package com.asher.bugfixer.openhands;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** Runs the prebuilt OpenHands worker image with a workflow-selected workspace and prompt. */
public final class DockerOpenHandsFixer implements OpenHandsFixer {
    private static final int MAX_OUTPUT_BYTES = 128 * 1024;
    private final AppConfig config;

    public DockerOpenHandsFixer(AppConfig config) {
        this.config = config;
    }

    @Override
    public FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception {
        if (!config.openhandsEnabled()) {
            return FixResult.disabled();
        }

        List<String> command = new ArrayList<>(List.of(
                "docker", "run", "--rm",
                "--mount", "type=bind,source=" + workspace.toAbsolutePath() + ",target=/workspace",
                "--workdir", "/workspace",
                "--entrypoint", "/app/runtime/openhands-container-worker.sh"));
        for (String name : workerEnvironmentNames()) {
            command.add("--env");
            command.add(name);
        }
        command.addAll(List.of(
                config.openhandsContainerImage(),
                "--workspace", "/workspace",
                "--prompt", OpenHandsPythonFixer.prompt(issue, repositoryName, validationFeedback)));
        ProcessBuilder builder = new ProcessBuilder(command).directory(workspace.toFile()).redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        configureWorkerEnvironment(environment);

        try (ExecutorService readers = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            System.out.println("OpenHands [" + issue.key() + "] launching container image="
                    + config.openhandsContainerImage() + " with a mounted isolated workspace.");
            Process process = builder.start();
            process.getOutputStream().close();
            Future<String> output = readers.submit(() -> readLimited(process.getInputStream()));
            boolean completed = process.waitFor(config.openhandsTimeout().toMillis(), TimeUnit.MILLISECONDS);
            if (!completed) {
                process.destroyForcibly();
                return new FixResult(true, false, -1, awaitOutput(output) + "\nOpenHands container timed out.");
            }
            return new FixResult(true, process.exitValue() == 0, process.exitValue(), awaitOutput(output));
        }
    }

    private void configureWorkerEnvironment(Map<String, String> environment) {
        environment.put("OPENHANDS_PROVIDER", config.openhandsProvider().name());
        environment.put("OPENHANDS_MODEL", config.openhandsModel());
        if (config.openhandsProvider() == OpenHandsProvider.GROQ) {
            putRequired(environment, "OPENHANDS_API_KEY", config.groqApiKey(), "GROQ_API_KEY");
            putRequired(environment, "OPENHANDS_BASE_URL", config.groqBaseUrl(), "GROQ_BASE_URL");
            return;
        }
        putRequired(environment, "OPENHANDS_API_KEY", config.openhandsGeminiApiKey(), "OPENHANDS_GEMINI_API_KEY or ADK_API_KEY");
        putRequired(environment, "GEMINI_BASE_URL", config.geminiConnectorBaseUrl(), "GEMINI_CONNECTOR_BASE_URL");
        putRequired(environment, "GEMINI_VERTEX_PROJECT", config.geminiVertexProject(), "GEMINI_VERTEX_PROJECT");
        putRequired(environment, "GEMINI_VERTEX_LOCATION", config.geminiVertexLocation(), "GEMINI_VERTEX_LOCATION");
        environment.put("GEMINI_API_KEY", config.openhandsGeminiApiKey());
        environment.put("GEMINI_PROXY_PORT", Integer.toString(config.geminiProxyPort()));
    }

    private List<String> workerEnvironmentNames() {
        if (config.openhandsProvider() == OpenHandsProvider.GROQ) {
            return List.of("OPENHANDS_PROVIDER", "OPENHANDS_MODEL", "OPENHANDS_API_KEY", "OPENHANDS_BASE_URL");
        }
        return List.of(
                "OPENHANDS_PROVIDER", "OPENHANDS_MODEL", "OPENHANDS_API_KEY",
                "GEMINI_BASE_URL", "GEMINI_VERTEX_PROJECT", "GEMINI_VERTEX_LOCATION",
                "GEMINI_API_KEY", "GEMINI_PROXY_PORT");
    }

    private void putRequired(Map<String, String> environment, String name, String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(propertyName + " must be configured when OpenHands is enabled.");
        }
        environment.put(name, value);
    }

    private String readLimited(InputStream stream) throws IOException {
        try (stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = stream.read(buffer)) != -1) {
                int remaining = MAX_OUTPUT_BYTES - output.size();
                if (remaining > 0) {
                    output.write(buffer, 0, Math.min(count, remaining));
                }
            }
            return output.toString(StandardCharsets.UTF_8);
        }
    }

    private String awaitOutput(Future<String> output) throws InterruptedException, ExecutionException {
        return output.get();
    }
}
