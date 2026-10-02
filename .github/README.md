# GitHub Actions - Ride-Hailing

## Flow

feature/* -> PR -> CI + Security -> develop -> release PR -> main -> tag -> Release

## Workflows

- `ci.yml`: Java 17, Gradle cache, wrapper validation, Docker Compose validation, unit tests, integration infrastructure, test reports.
- `security.yml`: Dependency Review on PRs, CodeQL for Java/Kotlin and Actions, weekly scan.
- `release.yml`: verifies tagged release, builds JARs, uploads artifacts and creates GitHub Release.
- `dependabot.yml`: weekly dependency/action updates.

## SRS alignment

Uses Java 17, Gradle, PostgreSQL, Kafka and Docker Compose as selected project tooling. The integration workflow assumes Compose services are named `postgres` and `kafka`, and that Gradle has an `integrationTest` task.

If the actual project uses another task name, change that one command.

## Branch protection

`develop`: PR + 1 approval + Build & Test + Integration + CodeQL.

`main`: PR + 2 approvals + Build & Test + Integration + CodeQL + no force push.

Never commit `.env`, credentials, JWT secrets, private keys or registry tokens.


//test Readme