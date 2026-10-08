# 0801 Modules integrate through published Java interfaces, not cross-table writes

| Field | Value |
|---|---|
| Status | accepted |
| Date | 2026-10-07 |

## Context

Order-service modules share one process and one database, so accidental tight coupling through another module's tables is easy. Background components like watchlists and notifications cannot rely on customer bearer tokens but still require scoped account behavior. Existing architecture already uses module interfaces such as account lookups, preference lookups, and alert delivery contracts.

## Options considered

| Option | For | Against |
|---|---|---|
| Let modules read/write each other's tables directly | Quick implementation with minimal boilerplate | Hard schema coupling and hidden ownership violations |
| Force loopback HTTP for all module-to-module calls | Mirrors external boundaries | Requires internal auth model and adds avoidable overhead in same process |
| Use published in-process Java interfaces for module boundaries | Compile-time contracts with clearer ownership seams | Still tied to single-process deployment model |

## Decision

Use published in-process Java interfaces as the default module boundary and avoid cross-table integration writes. The deciding factor was seam clarity and controlled coupling in current modular-monolith deployment.

## Consequences

- Module ownership and responsibilities are explicit and testable.
- Cost: extracting to independent services later requires remapping these interfaces to remote contracts.
- Cost: existing exceptions must be tracked and reduced deliberately, not ad hoc.
