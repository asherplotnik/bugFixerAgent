package com.asher.bugfixer.workflow;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Runs a fixed Git argument list without a shell. */
final class GitCommandRunner {
    private static final int MAX_OUTPUT_BYTES = 64 * 1024;

    private GitCommandRunner() {
    }

    static Result run(Path directory, List<String> arguments) throws Exception {
        List<String> command = new ArrayList<>(List.of("git"));
        command.addAll(arguments);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(directory.toFile())
                .redirectErrorStream(true);
        Map<String, String> environment = builder.environment();
        environment.remove("GITHUB_TOKEN");
        environment.remove("JIRA_API_TOKEN");
        Process process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Git command timed out: " + String.join(" ", command));
        }
        return new Result(process.exitValue(), readLimited(process.getInputStream()));
    }

    static void requireSuccess(Path directory, List<String> arguments, String action) throws Exception {
        Result result = run(directory, arguments);
        if (result.exitCode() != 0) {
            throw new IllegalStateException("Git failed while " + action + " (exit " + result.exitCode() + "): " + result.output());
        }
    }

    private static String readLimited(InputStream stream) throws IOException {
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

    record Result(int exitCode, String output) {
    }
}
