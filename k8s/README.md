# GKE rollout boundary

`webhook-receiver.yaml` deploys only the public receiver. It must not run the code-fixing worker and it has no Kubernetes Job permissions.

The next production increment is a Pub/Sub-backed dispatcher with permission to create one isolated Job for each accepted request. The Job will receive a request ID, obtain the current issue through Jira's REST API, and run the worker with a distinct Workload Identity. Build validation must use a further credential-free sandbox or GKE Sandbox because Maven and Gradle can execute repository-controlled code.

Do not apply the manifest until the image digest, Secret Manager integration, ingress, network policy, and durable queue are configured.
