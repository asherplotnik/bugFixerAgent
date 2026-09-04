package com.asher.bugfixer.openhands;

import com.asher.bugfixer.AppConfig;
import com.asher.bugfixer.domain.JiraIssue;
import io.fabric8.kubernetes.api.model.ContainerBuilder;
import io.fabric8.kubernetes.api.model.EnvVar;
import io.fabric8.kubernetes.api.model.EnvVarSourceBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimVolumeSourceBuilder;
import io.fabric8.kubernetes.api.model.PodSpecBuilder;
import io.fabric8.kubernetes.api.model.PodTemplateSpecBuilder;
import io.fabric8.kubernetes.api.model.SecretKeySelectorBuilder;
import io.fabric8.kubernetes.api.model.VolumeBuilder;
import io.fabric8.kubernetes.api.model.VolumeMountBuilder;
import io.fabric8.kubernetes.api.model.batch.v1.Job;
import io.fabric8.kubernetes.api.model.batch.v1.JobBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Creates one restricted Kubernetes Job for an already-prepared isolated workspace. */
public final class KubernetesOpenHandsFixer implements OpenHandsFixer {
    private static final int MAX_OUTPUT_CHARS = 128 * 1024;
    private final AppConfig config;

    public KubernetesOpenHandsFixer(AppConfig config) {
        this.config = config;
    }

    @Override
    public FixResult fix(JiraIssue issue, String repositoryName, Path workspace, String validationFeedback) throws Exception {
        requireConfiguration();
        String namespace = config.openhandsKubernetesNamespace();
        String jobName = jobName(issue.key());
        String workspaceSubPath = workspaceSubPath(workspace);
        String prompt = OpenHandsPythonFixer.prompt(
                issue, repositoryName, validationFeedback, InvestigationKnowledge.load(config));
        Job job = job(jobName, workspaceSubPath, prompt);

        try (KubernetesClient client = new KubernetesClientBuilder().build()) {
            System.out.println("OpenHands [" + issue.key() + "] creating Kubernetes Job=" + jobName
                    + " namespace=" + namespace + " image=" + config.openhandsContainerImage());
            client.batch().v1().jobs().inNamespace(namespace).resource(job).create();
            Job completed = client.batch().v1().jobs().inNamespace(namespace).withName(jobName)
                    .waitUntilCondition(this::isFinished, config.openhandsTimeout().toMillis(), TimeUnit.MILLISECONDS);
            String output = jobOutput(client, namespace, jobName);
            boolean succeeded = completed != null && completed.getStatus() != null
                    && completed.getStatus().getSucceeded() != null && completed.getStatus().getSucceeded() > 0;
            if (completed == null || !isFinished(completed)) {
                return new FixResult(true, false, -1, output + "\nOpenHands Kubernetes Job timed out.");
            }
            return new FixResult(true, succeeded, succeeded ? 0 : 1, output);
        }
    }

    private void requireConfiguration() {
        if (config.openhandsKubernetesSecretName() == null || config.openhandsKubernetesSecretName().isBlank()) {
            throw new IllegalStateException("OPENHANDS_KUBERNETES_SECRET_NAME must reference the model credential Secret.");
        }
        if (config.openhandsWorkspaceClaim() == null || config.openhandsWorkspaceClaim().isBlank()) {
            throw new IllegalStateException("OPENHANDS_WORKSPACE_CLAIM must reference the shared workspace PVC.");
        }
    }

    private Job job(String name, String workspaceSubPath, String prompt) {
        return new JobBuilder()
                .withNewMetadata().withName(name).addToLabels("app.kubernetes.io/name", "bug-fixer-openhands").endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withTtlSecondsAfterFinished(config.openhandsJobTtlSeconds())
                .withActiveDeadlineSeconds(config.openhandsTimeout().toSeconds())
                .withTemplate(new PodTemplateSpecBuilder()
                        .withNewMetadata().addToLabels("app.kubernetes.io/name", "bug-fixer-openhands").endMetadata()
                        .withSpec(new PodSpecBuilder()
                                .withServiceAccountName(config.openhandsKubernetesServiceAccount())
                                .withAutomountServiceAccountToken(false)
                                .withRestartPolicy("Never")
                                .withNewSecurityContext().withFsGroup(10001L).endSecurityContext()
                                .withVolumes(new VolumeBuilder()
                                        .withName("workspace")
                                        .withPersistentVolumeClaim(new PersistentVolumeClaimVolumeSourceBuilder()
                                                .withClaimName(config.openhandsWorkspaceClaim()).build())
                                        .build())
                                .withContainers(new ContainerBuilder()
                                        .withName("openhands")
                                        .withImage(config.openhandsContainerImage())
                                        .withImagePullPolicy("IfNotPresent")
                                        .withWorkingDir("/workspace")
                                        .withCommand("/app/runtime/openhands-container-worker.sh")
                                        .withArgs("--workspace", "/workspace", "--prompt", prompt)
                                        .withEnv(workerEnvironment())
                                        .withVolumeMounts(new VolumeMountBuilder()
                                                .withName("workspace").withMountPath("/workspace")
                                                .withSubPath(workspaceSubPath).build())
                                        .withNewSecurityContext()
                                        .withAllowPrivilegeEscalation(false)
                                        .withReadOnlyRootFilesystem(false)
                                        .withRunAsNonRoot(true)
                                        .withNewCapabilities().withDrop("ALL").endCapabilities()
                                        .endSecurityContext()
                                        .build())
                                .build())
                        .build())
                .endSpec()
                .build();
    }

    private List<EnvVar> workerEnvironment() {
        List<EnvVar> result = new ArrayList<>(List.of(
                literal("OPENHANDS_PROVIDER", config.openhandsProvider().name()),
                literal("OPENHANDS_MODEL", config.openhandsModel()),
                literal("GEMINI_PROXY_PORT", Integer.toString(config.geminiProxyPort()))));
        if (config.openhandsProvider() == OpenHandsProvider.GROQ) {
            result.add(literal("OPENHANDS_BASE_URL", config.groqBaseUrl()));
            result.add(secret("OPENHANDS_API_KEY", "groq-api-key"));
        } else {
            result.add(literal("GEMINI_BASE_URL", config.geminiConnectorBaseUrl()));
            result.add(literal("GEMINI_VERTEX_PROJECT", config.geminiVertexProject()));
            result.add(literal("GEMINI_VERTEX_LOCATION", config.geminiVertexLocation()));
            result.add(secret("OPENHANDS_API_KEY", "openhands-api-key"));
            result.add(secret("GEMINI_API_KEY", "gemini-api-key"));
        }
        return result;
    }

    private EnvVar literal(String name, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " must be configured for Kubernetes OpenHands execution.");
        }
        return new EnvVar(name, value, null);
    }

    private EnvVar secret(String name, String key) {
        return new EnvVar(name, null, new EnvVarSourceBuilder()
                .withSecretKeyRef(new SecretKeySelectorBuilder()
                        .withName(config.openhandsKubernetesSecretName()).withKey(key).build())
                .build());
    }

    private boolean isFinished(Job job) {
        return job != null && job.getStatus() != null
                && ((job.getStatus().getSucceeded() != null && job.getStatus().getSucceeded() > 0)
                || (job.getStatus().getFailed() != null && job.getStatus().getFailed() > 0));
    }

    private String jobOutput(KubernetesClient client, String namespace, String jobName) {
        StringBuilder output = new StringBuilder();
        client.pods().inNamespace(namespace).withLabel("job-name", jobName).list().getItems().forEach(pod -> {
            if (output.length() < MAX_OUTPUT_CHARS) {
                String log = client.pods().inNamespace(namespace).withName(pod.getMetadata().getName()).getLog();
                output.append(log, 0, Math.min(log.length(), MAX_OUTPUT_CHARS - output.length()));
            }
        });
        return output.toString();
    }

    private String workspaceSubPath(Path workspace) {
        Path root = config.workspaceRoot().toAbsolutePath().normalize();
        Path candidate = workspace.toAbsolutePath().normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalStateException("OpenHands workspace is outside the configured shared workspace root.");
        }
        String relative = root.relativize(candidate).toString().replace('\\', '/');
        if (relative.isBlank() || relative.startsWith("../")) {
            throw new IllegalStateException("OpenHands workspace subpath is invalid.");
        }
        return relative;
    }

    private String jobName(String issueKey) {
        String normalized = issueKey.toLowerCase().replaceAll("[^a-z0-9-]", "-");
        normalized = normalized.replaceAll("^-+|-+$", "");
        return "openhands-" + normalized + "-" + Long.toUnsignedString(System.nanoTime(), 36).substring(0, 7);
    }
}
