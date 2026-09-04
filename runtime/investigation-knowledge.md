# Trusted repository knowledge

- Bitbucket Data Center base URL: `https://bitbucket.dev.local:8443`.
- Project `DMS` contains the microservice repositories.
- Project `DIRM` contains the shared internal modules and dependencies used by those services.
- When an issue suggests shared dependency behavior, investigate the relevant `DIRM` module before proposing a service-local workaround.
- A fix may change only one repository. If the correct solution needs changes in multiple repositories, do not create a fix branch or pull request; report the required cross-repository work in Jira.
- Do not create, approve, or merge more than one pull request for an issue.

Add future repository, module, API, and ownership knowledge to this file. It is deployment-owned prompt context, not data supplied by Jira.
