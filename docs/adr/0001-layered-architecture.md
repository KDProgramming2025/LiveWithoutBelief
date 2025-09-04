---
status: Accepted
date: 2025-09-04
issue: LWB-18
deciders: core-team
---

# Decision: Enforce Clean Layered Modular Architecture

Domain (pure Kotlin, no Android) <- Data (infrastructure impl) <- Feature (UI + ViewModel) <- App (composition root).

# Rationale
- Testability: domain isolated, deterministic.
- Compile-time safety: direction is one-way, prevents accidental inward deps.
- Parallel work streams: features evolve without leaking infra concerns.

# Rules
1. Domain depends only on `core:model` + stdlib.
2. Data modules depend on domain for interfaces, never on feature.*
3. Feature modules depend on domain (+ injected abstractions), never directly on data impl packages.
4. App module wires Hilt bindings exposing data impl as domain interfaces.

# Enforcements
- Detekt + custom dependency guard task (regex import scan) in CI.
- Kover coverage gate (domain higher threshold later).

# Consequences
Positive: predictable boundaries, easier refactors. Negative: added indirection cost.

# Follow-up
- [ ] Raise domain coverage threshold.
- [ ] Add detekt custom rule (future).
