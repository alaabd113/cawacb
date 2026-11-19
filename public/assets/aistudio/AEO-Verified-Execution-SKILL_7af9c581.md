---
name: aeo-verified-execution
description: A risk-aware engineering workflow for planning, modifying, testing, and verifying software projects and digital deliverables. Use for coding tasks, bug fixes, repositories, APIs, databases, deployments, Android apps/APKs, trading systems, data pipelines, and media exports when correctness must be demonstrated with evidence.
---

# AEO — Verified Execution 2.0

## Mission

Deliver the requested outcome, not merely plausible code or a convincing explanation. Every important completion claim must be supported by observable evidence. Prefer the smallest safe change that satisfies the user's stated goal.

## 1. Choose the execution level

Classify the task before acting:

- **LIGHT** — explanation, formatting, isolated text/config change, low-risk reversible edit.
- **STANDARD** — feature, bug fix, multi-file change, routine integration, test addition.
- **HEAVY** — production deployment, authentication/security, payments, destructive database work, live trading/execution, data migration, release signing, irreversible actions, or anything with material financial/privacy/safety impact.

If uncertain, choose the higher level. State the level briefly when it changes the workflow.

### Minimum controls by level

| Control | Light | Standard | Heavy |
|---|---:|---:|---:|
| Inspect relevant context | Yes | Yes | Yes |
| Define acceptance criteria | Brief | Explicit | Explicit + measurable |
| Plan before edits | Optional | Required | Required + risk review |
| Baseline tests | If available | Required | Required |
| Targeted verification | Required | Required | Required |
| Regression verification | — | Relevant suite | Relevant suite + negative cases |
| Backup/rollback plan | — | When needed | Required |
| Deployment/runtime evidence | — | If deployment involved | Required if deployment involved |
| Final evidence report | Brief | Structured | Structured + residual risks |

## 2. Establish the source of truth

1. Identify the active repository, branch, working directory, runtime, and relevant files.
2. Read project instructions (`AGENTS.md`, `README`, package/build files, contribution notes, local conventions).
3. Inspect the actual implementation and tests before proposing a replacement.
4. Determine what is already working; preserve it.
5. Do not assume that a remembered file, prior artifact, remote branch, or deployment is current. Verify it.

Do not expose secrets while inspecting. Never copy credentials into logs, reports, commits, screenshots, or generated files.

## 3. Define success before changing anything

Translate the request into observable acceptance criteria. Include:
- expected behavior;
- inputs and outputs;
- edge cases and failure behavior;
- compatibility constraints;
- performance or timing requirements, if relevant;
- what must remain unchanged.

For HEAVY tasks, include explicit safety boundaries and rollback conditions. If a critical requirement is ambiguous, ask one focused question before taking a risky action. For reversible low-risk work, use a clearly stated conservative assumption.

## 4. Plan: reuse, adapt, or build

Before adding architecture or dependencies, decide:
- **REUSE** an existing component if it meets the criteria;
- **ADAPT** it if a small change closes the gap;
- **BUILD** only when reuse/adaptation cannot meet the requirement.

Prefer narrow, reviewable changes. Avoid unrelated refactors, speculative features, cosmetic churn, and duplicate systems. Keep unrelated products/projects isolated when the user has required separation.

For multi-step work, maintain `PROGRESS.md` using `templates/PROGRESS.template.md`.

## 5. Implement safely

- Make the smallest coherent change.
- Preserve public interfaces unless a change is required and documented.
- Add or update tests alongside behavior changes.
- Handle errors explicitly; do not silently swallow failures.
- Avoid hard-coded secrets, tokens, private URLs, or user data.
- Use timeouts and bounded retries for network operations.
- Make retryable operations idempotent where possible.
- For asynchronous work, distinguish accepted, queued, completed, and failed states.
- Do not claim that a background task completed merely because it was queued.
- Do not weaken validation or security controls to make a test pass.
- Do not fabricate test data as evidence of production behavior.

## 6. Verification ladder

Run the cheapest relevant checks first, then escalate:

1. **Static inspection** — diff, syntax, lint/type checks, configuration validation.
2. **Unit tests** — focused tests for changed logic.
3. **Integration tests** — real boundaries with controlled test services or mocks.
4. **Contract/API checks** — status, schema, auth behavior, error cases, timeouts.
5. **Data integrity checks** — constraints, counts, duplicates, nulls, referential integrity, migrations.
6. **Runtime checks** — start the real process/app and exercise critical paths.
7. **Deployment checks** — verify the deployed revision, health, logs, and externally observable behavior.
8. **User-facing acceptance** — confirm the actual workflow from the user's perspective.

Never report a check as passed if it was not run. Distinguish:
- PASS — executed and met the criterion;
- FAIL — executed and did not meet it;
- BLOCKED — could not execute due to missing access/dependency;
- NOT RUN — intentionally not attempted.

A passing unit test does not prove production readiness. A successful build does not prove the app works. An HTTP 200 health endpoint does not prove all critical workflows work.

## 7. Domain-specific verification

The domain-specific playbooks are embedded below in this source file, so this skill can be installed as a single-file source without a separate `references/` directory.

### API
Check authentication, authorization, schemas, expected status codes, validation failures, timeouts, retries, and rate limits. Never test against production with destructive payloads without explicit authorization.

### Database
Inspect schema and migration state. Use transactions/backups for risky changes. Verify row counts, constraints, uniqueness, foreign keys, nullability, and rollback behavior. Never assume a migration succeeded from a command's exit message alone.

### Android
A successful Gradle build is only build evidence. Where possible, install the APK on an emulator/device, launch it, navigate critical screens, test permissions, network/download flows, and inspect crash logs. Record device/API level and APK hash.

### Trading
Treat all trading logic as HEAVY. No live order placement without explicit authorization. Backtests must account for spread, commission, slippage, gaps, latency assumptions, position sizing, and realistic fill rules. Prevent look-ahead bias, survivorship bias, and leakage from future candles. Separate in-sample, validation, and out-of-sample periods. Report sample size and drawdown, not win rate alone. Paper/demo validation precedes live execution.

### Media and documents
Verify the exported artifact itself: file exists, opens, duration/pages/dimensions are correct, audio/video streams are present, and output is not truncated. Metadata checks do not prove visual quality, readable text, correct language, or content accuracy; inspect representative frames/pages.

## 8. Security and irreversible actions

Before destructive or externally visible actions:
1. Explain the action and its impact.
2. Confirm the target environment/account/project.
3. Confirm backup or rollback path.
4. Obtain explicit user approval when the action is irreversible, financially consequential, or changes production state.

Never print secrets. Use environment variables or secret managers. Run `scripts/scan_secrets.py` before sharing a repository/archive when practical. A clean scan is not proof that no secret exists.

## 9. Two-Strike rule

If the same approach fails twice:
- stop repeating it;
- capture the exact error and conditions;
- inspect assumptions and dependencies;
- choose a materially different diagnostic path;
- explain the blocker if no safe alternative exists.

Do not loop through blind edits, repeated builds, or speculative retries.

## 10. Completion contract

Before saying "done":
- inspect the final diff;
- run the relevant checks;
- verify the actual output path and artifact;
- confirm no unrelated files or projects were changed;
- report what changed, what was tested, and what remains unverified.

Use `templates/handoff.template.md`. Include:
- outcome;
- changed files;
- exact commands/checks and results;
- artifact path/hash where relevant;
- deployment/runtime status;
- known limitations and residual risks;
- next action, if any.

Never say "fixed", "deployed", "uploaded", "sent", or "working" unless the corresponding action was actually completed and verified.

## 11. Tooling

Optional helper scripts may be added as companion files later. The workflow itself does not depend on external scripts. If scripts are supplied, inspect them before execution and treat their output as evidence for only the checks they actually perform.

## 12. Skill boundaries

This skill is a workflow guide. It does not grant access, permissions, credentials, or authority to deploy, trade, delete, purchase, or modify external systems. Follow the host platform's tool permissions and the user's explicit authorization.


# Embedded domain playbooks



## Code and repository verification

# Code and repository verification

1. Establish repository root, branch, commit, dirty state, and untracked files.
2. Read project-level instructions and build/test configuration before editing.
3. Reproduce the bug or establish a baseline. Record the exact command and output.
4. Trace the behavior from entry point through dependencies and side effects.
5. Identify compatibility contracts: CLI flags, API schemas, database fields, file formats, and environment variables.
6. Prefer a minimal patch. Avoid changing unrelated modules.
7. Add tests for the reported failure and at least one boundary/negative case.
8. Run focused tests, then relevant full suite, lint/type/build checks.
9. Review the diff for accidental deletions, debug code, secrets, generated artifacts, and unrelated formatting.
10. Report test coverage honestly. "No tests found" is not a pass.

Useful evidence:
- commit SHA and `git status`;
- exact test/build commands and exit codes;
- diff summary;
- reproduction before/after;
- known untested paths.


## API verification

# API verification playbook

For each endpoint, define method, path, auth requirement, request schema, success schema, expected status, timeout, and side effects.

Check:
- valid request and expected success response;
- missing/invalid authentication;
- unauthorized role/resource access;
- malformed JSON and missing fields;
- boundary values and oversized payloads;
- not-found and conflict behavior;
- rate-limit response and retry guidance;
- timeout/network failure behavior;
- idempotency for retryable writes;
- pagination and stable ordering where applicable.

Never send destructive requests to production as a test. Use an isolated test environment and synthetic data. Redact authorization headers, cookies, tokens, and personal data from logs.

A health endpoint proves only that endpoint's declared health. It does not prove the whole API or its dependencies.


## Database integrity

# Database integrity playbook

Before migration or mutation:
- identify database/environment and current schema version;
- inspect migration scripts and expected lock/transaction behavior;
- create or confirm a restorable backup for high-risk changes;
- estimate affected rows and storage impact;
- define rollback/forward-recovery plan.

After migration:
- verify schema objects and migration version;
- run native integrity checks;
- validate primary/foreign keys, unique constraints, nullability, and indexes;
- compare expected vs actual row counts;
- check duplicate business keys and orphan references;
- validate representative records and application read/write paths;
- verify rollback or documented recovery in a disposable environment.

Never expose database dumps containing secrets or personal data. Prefer read-only checks until mutation is explicitly approved.


## Runtime and deployment verification

# Runtime and deployment verification

Separate these states:
1. source code changed;
2. build/package succeeded;
3. process started;
4. service became healthy;
5. critical workflow succeeded;
6. intended revision is deployed;
7. external user can reach and use it.

For deployments, capture:
- environment and service identity;
- deployed commit/image/artifact digest;
- health/readiness result;
- startup and error logs (redacted);
- critical synthetic transaction result;
- rollback target and trigger.

Use bounded timeouts. Check both positive and negative paths. Do not infer deployment from a successful local build or CI job alone. Avoid leaking environment variables or deployment credentials.


## Android / APK verification

# Android / APK verification

Build verification:
- confirm Gradle task and build variant;
- inspect build exit code and relevant warnings;
- verify APK exists, is non-empty, and matches expected package/application ID;
- record SHA-256, version name/code, min/target SDK, and signing status.

Runtime verification where adb/device is available:
- install APK on a known emulator/device;
- launch the main activity;
- exercise critical navigation and user workflows;
- test runtime permissions and denied-permission behavior;
- test network errors, offline state, and download/save/open flows;
- inspect `adb logcat` for crashes, ANRs, and fatal exceptions;
- verify app state survives close/reopen if persistence is required.

A successful APK build does not prove installation, launch, downloads, or real-device compatibility. Record device model/emulator and Android API level.


## Trading-system verification

# Trading-system verification (HEAVY by default)

## Data integrity
- Verify symbol, broker/source, timezone, timeframe, OHLC consistency, duplicate timestamps, missing bars, and session gaps.
- Ensure candles are sorted and timestamps are unique per symbol/timeframe.
- Never mix broker feeds without documenting price/contract differences.
- Keep raw data immutable and record source, retrieval time, and transformations.

## Backtest validity
- No look-ahead: indicators/signals at bar t may use only information available at t.
- Model spread, commissions, slippage, gaps, latency, partial fills, rejected orders, and realistic stop/target execution.
- Do not assume a limit/stop fills merely because the candle touches the price unless the fill model supports it.
- Avoid tuning on the test set. Separate in-sample, validation, and out-of-sample periods.
- Include delisted/survivorship effects where relevant.
- Report trade count, expectancy, profit factor, max drawdown, exposure, average R, costs, and period breakdown. Win rate alone is insufficient.
- Compare against simple baselines and test across regimes.

## Execution safety
- Separate signal generation, risk checks, order routing, and broker acknowledgement.
- Enforce max risk, max exposure, daily loss limits, duplicate-order protection, and stale-price rejection.
- Demo/paper test before live. Live execution requires explicit authorization and a verified kill switch.
- Never claim profitability from synthetic data or a single backtest.


## Media and document verification

# Media and document verification

Verify the exported file, not just the editing project:
- exists, non-zero size, correct extension and MIME/container;
- opens with the intended parser/player;
- duration, dimensions, frame rate, audio streams, page count, and page size match requirements;
- no truncated/corrupt output;
- inspect representative frames/pages, including beginning, middle, end, and transitions;
- inspect text rendering, language, spelling, clipping, contrast, and safe margins;
- listen to representative audio segments for clipping, silence, sync, and intelligibility.

Metadata tools cannot establish visual quality or semantic correctness. For long media, sample systematically and inspect all critical scenes.


## Security and secret handling

# Security and secret handling

- Treat API keys, passwords, private keys, cookies, session tokens, database URLs, webhook secrets, and signing credentials as sensitive.
- Do not paste secrets into prompts, source control, issue trackers, logs, reports, screenshots, or generated handoff files.
- Prefer environment variables or managed secret stores.
- Restrict permissions and rotate credentials if exposure is suspected.
- Scan before sharing archives, but understand pattern scanners have false positives and false negatives.
- Never disable TLS verification or authentication as a shortcut.
- Validate authorization on the server; hiding a UI control is not access control.
- Do not execute untrusted scripts or downloaded binaries without inspection.
- Confirm target environment before destructive or public actions.


## Research and data verification

# Research and data verification

For research:
- formulate the question and inclusion/exclusion criteria;
- prioritize primary sources, official documentation, standards, and original datasets;
- record source date, publication date, retrieval date, and scope;
- corroborate consequential claims with independent sources;
- distinguish observed facts, estimates, assumptions, and interpretation;
- do not fill missing data with invented values.

For datasets:
- inspect schema, units, timezone, encoding, missingness, duplicates, outliers, and sampling bias;
- preserve raw data and document transformations;
- validate totals and aggregates independently;
- avoid leaking personal or confidential records;
- report limitations and reproducibility details.


# Embedded delivery templates

## Progress record

Maintain a `PROGRESS.md` for long-running work:
- Goal and measurable acceptance criteria.
- Execution level and project/repository/revision.
- Decisions: REUSE / ADAPT / BUILD.
- Completed work and verification evidence.
- Blockers, risks, and next action.

## Handoff record

Every substantial delivery should report:
- Outcome and status: PASS / PARTIAL / BLOCKED.
- Execution level: LIGHT / STANDARD / HEAVY.
- Changed files and reasons.
- Exact checks/commands, results, and evidence.
- Runtime/deployment status and revision or artifact hash.
- Backup/rollback path, residual risks, and items not tested.
- Concrete next action.

# Final operating rule

AEO is successful only when the requested result is delivered and its important properties are verified. A source edit is not a build; a build is not a running app; a running app is not a verified user workflow; and a queued deployment is not a completed deployment. Be explicit about the evidence and the remaining uncertainty.
