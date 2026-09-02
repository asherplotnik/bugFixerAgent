# Bug Fixer Agent

A fail-closed local MVP for a Jira-triggered code-fix agent. It uses Java 21, Google ADK Java, and OpenCode as a constrained code-modification capability.

## Current implementation

- Spring Boot 4.1.1 exposes `POST /webhooks/jira` through `@RestController` and verifies Jira Cloud's HMAC signature over the raw body.
- It accepts only `jira:issue_updated` events that transition to `Ready for Agent` by default.
- It deduplicates Jira retry deliveries and responds quickly with `202 Accepted`.
- The optional worker re-fetches the issue through Jira REST, copies an approved local repository to an isolated workspace, runs OpenCode, then invokes only a fixed validation profile.
- ADK can wrap the OpenCode call (`ADK_ENABLED=true`), exposing no model-controlled command, path, repository, branch, or credential.
- Publishing is intentionally dry-run: it never commits, pushes, creates a PR, comments, or transitions a Jira issue.

## Run locally

1. Copy `src/main/resources/application.yaml.example` to `src/main/resources/application.yaml`. The real `application.yaml` is ignored by Git, so it can hold local development settings and credentials. Start with the development-only webhook secret `local-test-secret`; the worker is disabled by default, so this is webhook-only mode.
2. Run:

   ```powershell
   mvn test
   mvn spring-boot:run
   ```

3. Confirm `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

Kubernetes/production values still come from environment variables. Set a high-entropy `JIRA_WEBHOOK_SECRET` there; the local test secret must never be used outside development.

## Local PR simulation

The included `debugRepo` setup can exercise the complete local path without Jira. `LOCAL_SIMULATION_ENABLED=true` converts the signed webhook summary into a simulated, agent-ready Jira issue. This mode is strictly for local testing; it must be `false` in deployed environments, where the worker re-fetches Jira.

To enable a real **draft** PR, set `worker-enabled`, `opencode-enabled`, `adk-enabled`, and `publishing-enabled` to `true` in the ignored `src/main/resources/application.yaml`. Set `github-token` to a GitHub fine-grained token with **Contents: Read and write** and **Pull requests: Read and write** permissions for `asherplotnik/debugRepo`. Set the `adk-*` connector credentials in the same file. In GKE, Secret-backed `ADK_*` environment variables override those local values. The publisher uses a fixed target repository, creates a unique `bugfix/...` branch, and cannot be selected by Jira or OpenCode input.

For a controlled local worker test, set all of the following deliberately: `WORKER_ENABLED=true`, `JIRA_BASE_URL`, `JIRA_USER_EMAIL`, `JIRA_API_TOKEN`, `TARGET_REPOSITORY`, `OPENCODE_ENABLED=true`, and a non-`NONE` `VALIDATION_PROFILE`. The worker remains dry-run after validation.

## Security boundary

The OpenCode configuration denies its shell and web tools. Compilation happens outside OpenCode using a fixed argument list. This avoids model-provided shell commands but does **not** make Maven or Gradle safe: they can execute repository-controlled code. In GKE, validation must run in a credential-free sandbox, separate from the future publisher identity.

## Next increments

1. Replace the local in-memory queue with Pub/Sub and create one GKE Job per request.
2. Add a trusted publisher that applies an approved patch to a clean checkout and creates a draft PR using a short-lived SCM token.
3. Add Jira comments/status transitions only after a draft PR is created.
4. Add GKE Workload Identity, Secret Manager, egress policy, artifact retention, and a separate validation sandbox.
