# Specification: SPEC-012 - Gradle Wrapper Version Lock Check

## Metadata
- **Spec ID**: SPEC-012
- **Title**: Gradle Wrapper Version Lock Check
- **Status**: BACKLOG
- **Author**: AI Systems Architect
- **Target Files/Paths**:
  - `lua/cumulus/health.lua` (extends)

---

## Goal & Intent

Teams often have inconsistent Gradle wrapper versions across branches. Local dev uses `gradle-wrapper.properties` with version 7.6, but CI/CD (GitHub Actions, GitLab CI, Jenkinsfile) uses 8.0. This causes "it works on my machine" bugs and build reproducibility issues.

This spec adds a health check that compares local `gradle-wrapper.properties` version against CI configuration files:
- Read local Gradle version from `gradle-wrapper.properties`
- Search CI config (`.github/workflows/*.yml`, `.gitlab-ci.yml`, `Jenkinsfile`) for Gradle version
- If mismatch, warn: `⚠ Gradle version mismatch: local=7.6, CI=8.0`

---

## Scope Boundaries

**In scope:**
- Parse `gradle-wrapper.properties` distributionUrl for version
- Parse GitHub Actions, GitLab CI, Jenkins config for Gradle version
- Show mismatch warning in `:checkhealth cumulus`

**Out of scope:**
- Auto-update versions (requires user decision)
- Maven wrapper checks (separate spec if needed)
- Travis CI or other CI platforms (extend later)

---

## Prerequisite Analysis

- Health checks already exist in `lua/cumulus/health.lua`
- CI config files (YAML, Groovy DSL) can be parsed with regex

---

## Execution Checklist

- [ ] Extend `lua/cumulus/health.lua`:
  - [ ] Implement `check_gradle_version_consistency()` → reads local version, searches CI configs, reports mismatch
  - [ ] Add to health check report under "Build Tools" section

---

## Verification Commands

```bash
bash scripts/validate.sh
luac -p lua/cumulus/health.lua
nvim --headless "+checkhealth cumulus" +qa
```

### Acceptance Criteria
- [ ] `:checkhealth cumulus` shows Gradle version mismatch warning if present
- [ ] Warning shows both local and CI versions
- [ ] No warning if versions match

---

## Summary

Prevent "works locally but not in CI" bugs by detecting Gradle version mismatches early.
