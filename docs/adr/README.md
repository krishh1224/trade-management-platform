# Architecture Decision Records (ADRs)

This directory contains Architecture Decision Records (ADRs) documenting the major architectural decisions made in the Fauxnance Trading Platform.

## What is an ADR?

An Architecture Decision Record (ADR) is a short text file describing a set of forces and a single decided outcome in the context of your project. The intent is to capture important architectural decisions along with their context and consequences.

## ADRs in This Project

- **[0001-jwt-strategy.md](0001-jwt-strategy.md)** - Why HS256 JWT (symmetric) instead of RS256 (asymmetric)
- **[0002-kafka-partitioning.md](0002-kafka-partitioning.md)** - Why partition Kafka by accountId instead of orderId
- **[0003-event-sourcing.md](0003-event-sourcing.md)** - Current event-driven approach and future extensibility
- **[0004-executor-retry-strategy.md](0004-executor-retry-strategy.md)** - Exponential backoff for quote service failures

## Creating a New ADR

When making a significant architectural decision:

1. Create a new file: `NNNN-title-in-kebab-case.md`
2. Use the template below
3. Discuss with the team
4. Mark decision as "Accepted", "Rejected", or "Deprecated"

### ADR Template

```markdown
# ADR-NNNN: Title of Decision

## Status
[Proposed | Accepted | Deprecated]

## Context
Why we need to make a decision. What forces are at play?

## Decision
What we decided to do.

## Consequences
What implications does this decision have?

## Alternatives Considered
What other options did we evaluate and why were they rejected?

## Related ADRs
Links to related decisions.
```

## How to Update an ADR

- If a decision is superseded, update its Status to "Deprecated" and reference the new ADR
- Never delete historical ADRs; preserve decision history
- Add "Superseded by ADR-NNNN" note when deprecated

---

**Last Updated:** September 27, 2026
