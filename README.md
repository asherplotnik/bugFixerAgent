# Bug Fixer Agent

A fail-closed local MVP for a Jira-triggered code-fix agent. It uses Java 21, Google ADK Java, and OpenHands as a constrained code-modification capability.

## Current implementation

- Spring Boot 4.1.1 exposes `POST /webhooks/jira` through `@RestController` and verifies Jira Cloud's HMAC signature over the raw body.
- It accepts only `jira:issue_updated` events that transition to `Ready for Agent` by default.
- It deduplicates Jira retry deliveries and responds quickly with `202 Accepted`.
- The optional worker re-fetches the issue through Jira REST, copies an approved local repository to an isolated workspace, runs an OpenHands Python worker, then invokes only a fixed validation profile.
- The OpenHands worker is given only its file-editor tool. It has no terminal, browser, web, or custom network tool.
- ADK can wrap the OpenHands call (`ADK_ENABLED=true`), exposing no model-controlled command, path, repository, branch, or credential.
- Publishing is intentionally dry-run: it never commits, pushes, creates a PR, comments, or transitions a Jira issue.

## Run locally

1. Copy `src/main/resources/application.yaml.example` to `src/main/resources/application.yaml`. The real `application.yaml` is ignored by Git, so it can hold local development settings and credentials. Start with the development-only webhook secret `local-test-secret`; the worker is disabled by default, so this is webhook-only mode.
2. Run:

   ```powershell
   mvn test
   mvn spring-boot:run
   ```

   The first OpenHands run also needs its Python worker dependencies. For local development, install its explicit file-editor-only runtime once:

   ```powershell
   python -m pip install -r runtime/openhands-requirements.txt
   python -m pip install --no-deps -r runtime/openhands-tools-requirements.txt
   ```

3. Confirm `GET http://localhost:8080/actuator/health` returns `{"status":"UP"}`.

Kubernetes/production values still come from environment variables. Set a high-entropy `JIRA_WEBHOOK_SECRET` there; the local test secret must never be used outside development.

## Local PR simulation

The included `debugRepo` setup can exercise the complete local path without Jira. `LOCAL_SIMULATION_ENABLED=true` converts the signed webhook summary into a simulated, agent-ready Jira issue. This mode is strictly for local testing; it must be `false` in deployed environments, where the worker re-fetches Jira.

To enable a real **draft** PR, set `worker-enabled`, `openhands-enabled`, `adk-enabled`, and `publishing-enabled` to `true` in the ignored `src/main/resources/application.yaml`. Set `github-token` to a GitHub fine-grained token with **Contents: Read and write** and **Pull requests: Read and write** permissions for `asherplotnik/debugRepo`.

Set `openhands-provider: GEMINI` to use the same Gemini connector credentials as ADK, through the loopback compatibility bridge. Set `openhands-provider: GROQ`, `openhands-model`, and `groq-api-key` to use Groq's OpenAI-compatible endpoint directly. The publisher uses a fixed target repository, creates a unique `bugfix/...` branch, and cannot be selected by Jira or OpenHands input.

## Bitbucket Data Center publishing

Set `BITBUCKET_BASE_URL=https://bitbucket.dev.local:8443`, `BITBUCKET_PROJECT_KEY` to `DMS` or `DIRM`, `TARGET_REPOSITORY_NAME`, and `BITBUCKET_TOKEN`. With `PUBLISHING_ENABLED=true`, the trusted Java workflow clones `https://bitbucket.dev.local:8443/scm/<project>/<repository>.git`, commits the validated diff to `bugfix/<jira-key>`, pushes it, and creates one Bitbucket pull request to the configured target branch. The token is supplied only to Java's Git and REST calls; it is never passed into OpenHands.

For a controlled local worker test, set all of the following deliberately: `WORKER_ENABLED=true`, `JIRA_BASE_URL`, `JIRA_USER_EMAIL`, `JIRA_API_TOKEN`, `TARGET_REPOSITORY`, `OPENHANDS_ENABLED=true`, a selected `OPENHANDS_PROVIDER`, and a non-`NONE` `VALIDATION_PROFILE`. The worker remains dry-run after validation.

## One-shot worker mode

Set `WORKER_MODE=true` to run one workflow and exit, which is the execution mode intended for a Kubernetes Job. It does not start the HTTP server. Provide `JOB_ISSUE_KEY` and, when useful, `JOB_ISSUE_ID`, `JOB_DELIVERY_ID`, and `JOB_ISSUE_SUMMARY` as trusted Job environment variables. This mode does not require a Jira webhook secret because it never receives webhooks.

Set `OPENHANDS_CONTAINER_ENABLED=true` to launch each OpenHands attempt in the configured `OPENHANDS_CONTAINER_IMAGE`. Java supplies a fixed Docker invocation, mounts only the prepared workspace at `/workspace`, and passes the trusted model configuration. The model cannot supply Docker arguments, an image name, or a mount path. Build the dedicated worker image with `docker build -f Dockerfile.openhands --tag bug-fixer-openhands:0.1.1 .`.

## Security boundary

OpenHands has only the file-editor tool. Compilation happens outside OpenHands using a fixed argument list. This avoids model-provided shell commands but does **not** make Maven or Gradle safe: they can execute repository-controlled code. In GKE, validation must run in a credential-free sandbox, separate from the future publisher identity.

## Next increments

1. Replace the local in-memory queue with Pub/Sub and create one GKE Job per request.
2. Add Jira comments/status transitions only after a draft PR is created.
3. Add GKE Workload Identity, Secret Manager, egress policy, artifact retention, and a separate validation sandbox.
