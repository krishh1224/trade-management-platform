---
name: QA-Testing Agent Instructions
description: "Use when operating the QA-Testing Agent (manual trigger) for efficient change-based testing with coverage gating and human checkpoints."
---
# QA-Testing Agent Standard

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

## Coverage Loop
Steps 3 and 4 repeat until every measured module in scope meets the coverage threshold.
- After each iteration, read coverage for each measured module.
- At or above threshold for all measured modules: exit the loop and run step 5.
- Below threshold for any module: run another iteration (gap analysis, focused tests, rerun) for the below-threshold modules only.
- Do not ask the user between iterations; the Test Generation Checkpoint approval covers the whole loop.
- Fix failing tests that this run added before adding more tests.
- Stop early only when:
  - no below-threshold module's coverage has increased for 2 consecutive iterations, or
  - the remaining gaps need production code changes, unavailable infrastructure, or are blocked by pre-existing test failures, or
  - the user interrupts.
- On an early stop, go to the Gate Checkpoint with the reason.
- After each iteration, post one progress line per module: iteration number, coverage before -> after, tests added.

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
| Module | Path | Tooling | Legacy alias |
|---|---|---|---|
| order-service | `services/order-service` | Java + JaCoCo | trade-api |
| executor-service | `services/executor-service` | Java + JaCoCo | executor |
| auth-service | `services/auth-service` | NestJS + Jest coverage | nest-auth |
| advisor-api | `advisor-api` | Java + JaCoCo (agent attached from the command line) | |
| trade-ui | `frontend/trade-ui` | Angular + Vitest coverage | |
| trade-ui-e2e | `frontend/trade-ui` | Playwright (opt-in, no coverage) | |
| analytics | `databases/duckdb/analytics` | pytest-cov (optional) | |

Scope groups:
- `services`: order-service, executor-service, auth-service, advisor-api
- `frontend`: trade-ui (add trade-ui-e2e only when named explicitly)
- `full`: every module above except trade-ui-e2e

Legacy aliases resolve to the current module, so `trade-api` means `order-service`.

## Changed-File to Module Mapping
- `services/order-service/**` -> order-service
- `services/executor-service/**` -> executor-service
- `services/auth-service/**` -> auth-service
- `advisor-api/**` -> advisor-api
- `frontend/trade-ui/e2e/**`, `frontend/trade-ui/playwright.config.ts` -> trade-ui-e2e (report as impacted; run only if the user opts in)
- `frontend/trade-ui/**` (everything else) -> trade-ui
- `databases/duckdb/analytics/**` -> analytics
- `contracts/*.yaml` -> modules that consume the contract (trade-api.yaml: order-service, trade-ui; auth-api.yaml: auth-service, trade-ui; advisor-api.yaml: advisor-api, trade-ui)
- `databases/postgresql/**`, `infrastructure/**`, `docker-compose.yml`, `.env*`, `docs/**`, `*.md`: config/docs only; no module run unless requested

## Script-Free Baseline Commands
Run each command from the module's own directory.
- order-service: `mvnw.cmd test jacoco:report`
- executor-service: `mvnw.cmd test jacoco:report`
- auth-service: `npm test -- --coverage --coverageReporters=text-summary --coverageReporters=json-summary --watchAll=false`
- advisor-api: `mvn org.jacoco:jacoco-maven-plugin:0.8.12:prepare-agent test org.jacoco:jacoco-maven-plugin:0.8.12:report` (no Maven wrapper or JaCoCo plugin in its pom, so system Maven is used and JaCoCo is attached on the command line)
- trade-ui: `npx ng test --watch=false --coverage --coverage-reporters=text-summary --coverage-reporters=json-summary`
- trade-ui-e2e: `npm run test:e2e` (requires the app stack running; excluded from the coverage gate)
- analytics: `pytest trade_etl/tests --cov=. --cov-report=term-missing --cov-report=xml` (tests live under `trade_etl/tests`, not the `tests` path in pyproject.toml)

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

2. Test Generation Checkpoint (once, before the first iteration):
- Confirm proposed test files before writing.
- In the same message, provide numbered options:
  1. Approve and iterate until threshold
  2. Revise plan
  3. Skip generation

3. Gate Checkpoint (only when the coverage loop stops early):
- Not shown when the threshold is met.
- Show coverage per module, iteration history, and the stop reason.
- In the same message, provide numbered options:
  1. Resume loop with a revised approach
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
- Coverage per module, with coverage after each iteration
- Coverage report paths (for modules in scope):
  - services/order-service/target/site/jacoco/index.html
  - services/executor-service/target/site/jacoco/index.html
  - advisor-api/target/site/jacoco/index.html
  - services/auth-service/coverage/coverage-summary.json
  - frontend/trade-ui/coverage/ (Vitest output; report the summary file the run produced)
  - databases/duckdb/analytics/coverage.xml
- Agent report paths:
  - .agent-output/qa-testing/latest/report.json
  - .agent-output/qa-testing/latest/report.html
- Final status and next recommended step

## Report Persistence Rule
- Every run must replace the previous agent output.
- The canonical output directory is fixed at `.agent-output/qa-testing/latest`.
- No timestamped run folders should be retained by default.
- When no script is used, the agent must write summary files directly to:
  - `.agent-output/qa-testing/latest/report.json`
  - `.agent-output/qa-testing/latest/report.html`

## Practical Guidance
- Keep generated tests focused on newly introduced or changed logic paths.
- Prioritize deterministic tests with clear assertions.
- Keep each iteration small (target the largest uncovered areas first) so coverage climbs steadily; the loop, not one large iteration, closes the gap.
- At each checkpoint, ask the user to respond with only the option number.
