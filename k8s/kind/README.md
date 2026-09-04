# Local Kind simulation

`bug-fixer-kind.yaml` runs the Java webhook receiver and grants it namespace-only Job and Pod-log access. It uses a Kind-only `hostPath` PVC so the Java service and an OpenHands Job can mount the same isolated workspace.

The manifest enables the full local flow: `WORKER_ENABLED=true`, `OPENHANDS_EXECUTION_MODE=KUBERNETES`, and `PUBLISHING_ENABLED=true`. `openhands-smoke-job.yaml` separately proves the published OpenHands worker image starts and completes as a Kubernetes Job.

Before the first run, prepare the Kind-only hostPath volume for the non-root application user. Kind creates this directory as root, and `fsGroup` does not change ownership for `hostPath` volumes:

```powershell
docker exec bug-fixer-control-plane sh -c 'mkdir -p /var/lib/bug-fixer-workspace && chown 10001:10001 /var/lib/bug-fixer-workspace && chmod 0770 /var/lib/bug-fixer-workspace'
```

For a Gemini run, the Secret named by `OPENHANDS_KUBERNETES_SECRET_NAME` must contain both `openhands-api-key` and `gemini-api-key`. They hold the same Gemini connector key: the OpenHands SDK receives the first key and the bundled Gemini compatibility bridge receives the second. The Java configuration accepts `OPENHANDS_GEMINI_API_KEY` or the `ADK_API_KEY` fallback, but it does not copy either value into Kubernetes; create the Secret through your local setup or Helm/External Secrets process.

For a real local end-to-end run, create local-only model and repository Secrets referenced by the manifest. Do not use the local simulation Secret or Kind hostPath PV in OpenShift; use an approved shared PVC that supports the OpenShift pod security context instead.
