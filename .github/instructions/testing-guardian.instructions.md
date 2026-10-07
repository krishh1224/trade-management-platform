---
name: Testing Guardian Instructions
description: "Use when operating the Testing Guardian manual agent for efficient change-based testing with coverage gating and human checkpoints."
---
# Testing Guardian Standard

## Goal
Keep testing fast and relevant for code changes while maintaining a reliable coverage floor.

## Default Policy
- Manual run only.
- Default scope: changed modules.
- Default minimum coverage: 80%.
- Coverage is one gate, not the whole definition of quality.

## Fixed Run Path (Always 5 Steps)
1. Scope and classify changes.
2. Baseline run in approved scope.
3. Gap analysis (tests, coverage, artifacts).
4. Targeted test improvement (optional, with approval).
5. Final gate rerun and publish latest reports.

At the beginning of each run, publish this as a 5-item todo list and update status as each step progresses.

## Run Modes
1. changed (default):
- Detect changed files from Git and run only impacted modules.
- Skip heavy runs for modules with config-only changes unless explicitly requested.

2. module:
- Run selected module(s) only.

3. full:
- Run all supported modules.

## Supported Modules
- trade-api (Java + JaCoCo)
- executor (Java + JaCoCo)
- nest-auth (Jest coverage)
- analytics (pytest-cov, optional)

## Script-Free Baseline Commands
- trade-api: `mvnw.cmd test jacoco:report`
- executor: `mvnw.cmd test jacoco:report`
- nest-auth: `npm test -- --coverage --coverageReporters=text-summary --coverageReporters=json-summary --watchAll=false`
- analytics: `pytest --cov=. --cov-report=term-missing --cov-report=xml`

Run only scoped modules unless full mode is selected.

## Human Checkpoints
Implementation note:
- Use a script-free flow. Keep the prompt running in the same session and collect numbered checkpoint choices directly in chat.

1. Scope Checkpoint:
- Confirm selected scope and impacted modules.
- In the same message, provide numbered options:
  1. Proceed
  2. Expand to full workspace
  3. Custom scope

2. Test Generation Checkpoint:
- Confirm proposed test files before writing.
- In the same message, provide numbered options:
  1. Approve edits
  2. Revise plan
  3. Skip generation

3. Gate Checkpoint:
- Confirm whether to iterate when threshold is missed.
- In the same message, provide numbered options:
  1. Iterate once
  2. Finish with current result
  3. Stop with diagnostics

## Gate Decision
- PASS:
  - tests pass in scope
  - measured coverage for scoped modules >= threshold
- REVIEW:
  - tests pass, but one or more modules could not produce coverage artifacts
- FAIL:
  - test failures or measured coverage below threshold

## Required End-of-Run Output
- Scope and changed files summary
- Commands executed per module
- Coverage per module
- Java report paths:
  - trade-api/target/site/jacoco/index.html
  - executor/target/site/jacoco/index.html
- Agent report paths:
  - .agent-output/testing-guardian/latest/report.json
  - .agent-output/testing-guardian/latest/report.html
- Final status and next recommended step

## Report Persistence Rule
- Every run must replace the previous agent output.
- The canonical output directory is fixed at `.agent-output/testing-guardian/latest`.
- No timestamped run folders should be retained by default.
- When no script is used, the agent must write summary files directly to:
  - `.agent-output/testing-guardian/latest/report.json`
  - `.agent-output/testing-guardian/latest/report.html`

## Practical Guidance
- Keep generated tests focused on newly introduced or changed logic paths.
- Prioritize deterministic tests with clear assertions.
- Keep iteration count small (1-2 rounds) to control runtime.
- At each checkpoint, ask the user to respond with only the option number.
