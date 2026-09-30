# Git Workflow Scripts — Ride-Hailing Microservices

## Branch strategy

```text
main
  ^
  | release PR
develop
  ^
  | Pull Request
feature/m1/*
feature/m2/*
feature/m3/*
```

## Team ownership

- `m1`: Identity + Driver + Security
- `m2`: Trip + Matching + Concurrency + Saga
- `m3`: Pricing + Payment + Notification + Kafka + Monitoring/Deployment

## Scripts

### Initialize

```powershell
.\scripts\git-start.ps1 -RemoteUrl "https://github.com/<org>/<repo>.git"
```

Run once only when the local directory has no `.git`.

### Create feature

```powershell
.\scripts\git-feature.ps1 m1 identity-service
.\scripts\git-feature.ps1 m2 matching-service
.\scripts\git-feature.ps1 m3 payment-service
```

### Commit + push

```powershell
.\scripts\git-push.ps1 "feat(matching): implement driver matching"
```

The script blocks direct commits to `main` and `develop`.

### Sync feature with develop

```powershell
.\scripts\git-sync.ps1
```

It fetches `origin`, updates `develop`, rebases the feature branch and pushes with `--force-with-lease`.

### PR pre-check

```powershell
.\scripts\git-pr-check.ps1
```

### Status

```powershell
.\scripts\git-status.ps1
```

### Release

```powershell
.\scripts\git-release.ps1 v1.0.0
```

Then create PR:

```text
release/v1.0.0 -> main
```

After release validation:

```powershell
.\scripts\git-finish-release.ps1 v1.0.0
```

### Cleanup merged local branches

```powershell
.\scripts\git-clean-merged.ps1
```

## Daily workflow

```powershell
.\scripts\git-feature.ps1 m2 trip-service

# code...

.\scripts\git-push.ps1 "feat(trip): implement trip creation"

.\scripts\git-sync.ps1
.\scripts\git-pr-check.ps1

# GitHub:
# feature/m2/trip-service -> develop
# review -> CI -> merge
```

## Team rules

1. No direct commits to `main`.
2. No direct commits to `develop`.
3. Feature branches start from `develop`.
4. Integration happens through Pull Requests.
5. Never commit `.env`, credentials, JWT secrets, private keys or tokens.
6. No cross-service database access.
7. Keep API/event contracts documented.
8. Prefer small PRs.
9. `git-sync.ps1` uses `--force-with-lease`, never plain `--force`.
10. `develop` should remain buildable and testable.
