# GitHub Agents

This directory contains custom VS Code agents that enhance the development workflow for the Artha trading system.

## Available Agents

### QA-Testing Agent

**Purpose:** Ensure every meaningful code change gets efficient testing with a safety gate based on code coverage.

**Trigger:** Manual invocation only (never auto-runs).

**Key Features:**
- Change-based testing detection
- Coverage threshold enforcement (default: 80%)
- Iterative test generation until coverage targets are met
- Focused testing on impacted modules only
- Human checkpoints at critical decision points

---

## QA-Testing Agent Workflow

### 5-Step Fixed Run Path

```mermaid
flowchart TD
    Start([Agent Triggered Manually]) --> Step1["<b>Step 1: Scope & Classify</b><br/>Detect changed files<br/>Identify impacted modules<br/>Classify change type"]
    
    Step1 --> CheckpointA["<b>Checkpoint A: Scope</b><br/>Present change summary<br/>Ask user confirmation"]
    
    CheckpointA -->|User Approval| Step2["<b>Step 2: Baseline Run</b><br/>Execute test commands<br/>per module in scope"]
    
    Step2 --> Step3["<b>Step 3: Gap Analysis</b><br/>Read coverage reports<br/>Identify uncovered code<br/>List failing tests"]
    
    Step3 --> CoverageCheck{"Coverage at<br/>or above<br/>threshold?"}
    
    CoverageCheck -->|Yes| Step5["<b>Step 5: Final Gate</b><br/>Rerun scope<br/>Publish latest reports"]
    
    CoverageCheck -->|No| CheckpointB["<b>Checkpoint B: Test Plan</b><br/>Show proposed tests<br/>Ask for approval<br/>(once per run)"]
    
    CheckpointB -->|Approved| Step4["<b>Step 4: Targeted Tests</b><br/>Write focused tests<br/>for largest gaps<br/>Fix failing tests"]
    
    Step4 --> Rerun["Rerun module tests<br/>Measure new coverage"]
    
    Rerun --> NoProgress{"Coverage improved<br/>in last 2<br/>iterations?"}
    
    NoProgress -->|Yes| CoverageCheck
    
    NoProgress -->|No| CheckpointC["<b>Checkpoint C: Gate</b><br/>Show coverage per module<br/>State stop reason<br/>Ask how to proceed"]
    
    CheckpointB -->|Skip Tests| Step5
    
    CheckpointC -->|Resume| Step4
    
    CheckpointC -->|Finish| Step5
    
    Step5 --> Publish["Publish Coverage Reports<br/>.agent-output/qa-testing/latest/"]
    
    Publish --> Complete([Run Complete<br/>PASS/REVIEW/FAIL])
```

### Run Modes

```mermaid
graph LR
    Mode["Run Mode Selection"]
    
    Mode -->|changed| Changed["Default<br/>Run only impacted<br/>modules from git diff"]
    
    Mode -->|module| Module["Explicit<br/>Run specific<br/>named modules"]
    
    Mode -->|full| Full["Complete<br/>Run all supported<br/>modules"]
```

### Supported Modules & Commands

```mermaid
graph TD
    Modules["Supported Modules"]
    
    subgraph Services
        OS["<b>order-service</b><br/>mvnw.cmd test jacoco:report<br/>JaCoCo coverage"]
        ES["<b>executor-service</b><br/>mvnw.cmd test jacoco:report<br/>JaCoCo coverage"]
        AS["<b>auth-service</b><br/>npm test -- --coverage<br/>Jest coverage"]
    end
    
    subgraph Frontend
        UI["<b>trade-ui</b><br/>npx ng test --coverage<br/>Vitest coverage"]
        E2E["<b>trade-ui-e2e</b><br/>npm run test:e2e<br/>Playwright opt-in"]
    end
    
    subgraph Analytics
        Ana["<b>analytics</b><br/>pytest --cov<br/>pytest coverage"]
    end
    
    Modules --> Services
    Modules --> Frontend
    Modules --> Analytics
```

### Coverage Loop (Steps 3-4 Iteration)

```mermaid
flowchart TD
    LoopStart["Gap Analysis<br/>on Failed Coverage"] --> Identify["Identify uncovered<br/>methods, branches, lines"]
    
    Identify --> Prioritize["Prioritize largest<br/>gaps first"]
    
    Prioritize --> WriteTests["Write focused tests<br/>for uncovered code"]
    
    WriteTests --> FixFailing{"Tests added<br/>by this run<br/>failing?"}
    
    FixFailing -->|Yes| FixTests["Fix failing tests<br/>before adding more"]
    
    FixTests --> Rerun
    
    FixFailing -->|No| Rerun["Rerun module<br/>test command<br/>Measure coverage"]
    
    Rerun --> Compare{"Coverage<br/>increased?"}
    
    Compare -->|No 2nd time| Stop["STOP EARLY<br/>Blocked or no progress"]
    
    Compare -->|Yes| CheckThreshold{"All modules<br/>at or above<br/>threshold?"}
    
    CheckThreshold -->|Yes| ExitLoop["EXIT LOOP<br/>Proceed to Step 5"]
    
    CheckThreshold -->|No| LoopStart
```

### Human Checkpoints

#### Checkpoint A: Scope Confirmation
```
Input:
- List of changed files
- Impacted modules detected
- Change classification (code vs config)

Output Options:
1. Proceed with proposed scope
2. Expand scope to full workspace  
3. Provide custom scope
```

#### Checkpoint B: Test Plan Approval
```
Input:
- Proposed test files to create/modify
- Notification that loop will iterate until threshold

Output Options:
1. Approve and iterate until threshold met
2. Revise the test plan
3. Skip test generation (keep current results)

Note: Approval covers all iterations; not asked again between iterations
```

#### Checkpoint C: Gate Outcome (Early Stop Only)
```
Input (only if coverage threshold not met):
- Coverage per module
- Iteration history
- Reason for early stop (no progress, blocked, or user interrupt)

Output Options:
1. Resume loop with revised approach
2. Finish run and keep current result
3. Stop and return diagnostics only

Note: Skipped entirely if threshold is met; goes straight to Step 5
```

---

## Coverage Gate Decision Logic

```mermaid
graph TD
    Gate["Quality Gate"]
    
    TestStatus{"Tests<br/>Pass?"}
    
    TestStatus -->|No| Fail["❌ FAIL"]
    
    TestStatus -->|Yes| Coverage{"Coverage<br/>measured for<br/>all modules?"}
    
    Coverage -->|No| Review["🔍 REVIEW<br/>Some modules lack<br/>coverage artifacts"]
    
    Coverage -->|Yes| Threshold{"All modules<br/>≥ threshold<br/>80% default?"}
    
    Threshold -->|No| Fail
    
    Threshold -->|Yes| Pass["✅ PASS"]
```

---

## End-of-Run Output

All runs produce standardized output:

### Coverage Reports (Per Module)
- **order-service:** `services/order-service/target/site/jacoco/index.html`
- **executor-service:** `services/executor-service/target/site/jacoco/index.html`
- **auth-service:** `services/auth-service/coverage/coverage-summary.json`
- **trade-ui:** `frontend/trade-ui/coverage/` (Vitest summary)
- **analytics:** `databases/duckdb/analytics/coverage.xml`

### Agent Output
- **Report JSON:** `.agent-output/qa-testing/latest/report.json`
- **Report HTML:** `.agent-output/qa-testing/latest/report.html`

### Summary Information
- Scope and changed files
- Commands executed per module
- Coverage before/after each iteration
- Final gate status: PASS / REVIEW / FAIL
- Next recommended step

---

## Run Examples

### Default: Test Changed Modules
```
Agent scope: changed
Affected modules: order-service, trade-ui (detected from git diff)
Threshold: 80% (default)
```

### Explicit Module
```
Agent scope: module
Named module: order-service
Threshold: 75% (custom)
```

### Full Workspace
```
Agent scope: full
All modules: order-service, executor-service, auth-service, trade-ui, analytics
Threshold: 80% (default)
```

---

## Design Principles

1. **Manual Trigger Only:** Agent never auto-runs; prevents CI/CD interference.
2. **Fixed 5-Step Path:** Consistent flow across all invocations.
3. **Iterative Coverage Loop:** Repeats until threshold is met, not just once.
4. **Human Checkpoints:** Three decision gates keep the user in control.
5. **Change-Focused:** Default scope targets only impacted modules.
6. **Script-Free:** Direct command execution; no complex orchestration scripts.
7. **One Output:** Latest report replaces previous; no timestamped folders.

---

## Configuration Files

- **Agent Definition:** [qa-testing.agent.md](agents/qa-testing.agent.md)
- **Instructions & Standards:** [qa-testing.instructions.md](instructions/qa-testing.instructions.md)

